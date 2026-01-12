package com.wuxiansheng.shieldarch.marsdata.scheduler.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.CityMap;
import com.wuxiansheng.shieldarch.marsdata.config.PatrolConfig;
import com.wuxiansheng.shieldarch.marsdata.config.PatrolConfigService;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.marsdata.scheduler.LockedTask;
import com.wuxiansheng.shieldarch.marsdata.scheduler.repository.IntegrityCheckGroupResult;
import com.wuxiansheng.shieldarch.marsdata.scheduler.repository.IntegrityRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * GD冒泡完整性校验任务
 */
@Slf4j
@Component
public class IntegrityCheckTask implements LockedTask {

    /**
     * 每小时的 15 和 45 分执行：0 15,45 * * * *
     */
    private static final String CRON_EXPRESSION = "0 15,45 * * * *";

    /**
     * const integrityCheckLockKey = "gd_bubble_integrity_check_task_lock"
     * const integrityCheckLockTTL = 5 * 60 * time.Second
     */
    private static final String LOCK_KEY = "gd_bubble_integrity_check_task_lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private static final String INTEGRITY_MISSING_COUNT_METRIC = "integrity_missing_count";
    private static final String INTEGRITY_ACTUAL_COUNT_METRIC = "integrity_actual_count";

    private static final DateTimeFormatter TIME_RANGE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IntegrityRepository integrityRepository;
    private final PatrolConfigService patrolConfigService;
    private final MetricsClientAdapter metricsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntegrityCheckTask(IntegrityRepository integrityRepository,
                              PatrolConfigService patrolConfigService,
                              MetricsClientAdapter metricsClient) {
        this.integrityRepository = integrityRepository;
        this.patrolConfigService = patrolConfigService;
        this.metricsClient = metricsClient;
    }

    @Override
    public String getName() {
        return "IntegrityCheckTask";
    }

    @Override
    public void execute() {
        log.info("[IntegrityCheckTask] 执行GD冒泡完整性校验任务: {}", getName());
        executeIntegrityCheck();
    }

    @Override
    public String getSchedule() {
        return CRON_EXPRESSION;
    }

    @Override
    public String getLockKey() {
        return LOCK_KEY;
    }

    @Override
    public Duration getLockTTL() {
        return LOCK_TTL;
    }

    // ==================== 核心执行逻辑 ====================

    /**
     * 执行完整性校验的核心逻辑，包括时间范围提取、配置获取、数据收集和对比
     */
    void executeIntegrityCheck() {
        Map<String, Map<String, Map<String, Integer>>> citiesToCheck = getCitiesToCheck();
        if (citiesToCheck.isEmpty()) {
            log.info("[IntegrityCheckTask] 未找到待校验的城市配置");
            return;
        }

        try {
            String resultJson = objectMapper.writeValueAsString(citiesToCheck);
            log.info("[IntegrityCheckTask] getCitiesToCheck: 找到 {} 个匹配的时间范围配置, result={}", citiesToCheck.size(), resultJson);
        } catch (JsonProcessingException e) {
            log.warn("[IntegrityCheckTask] 序列化 citiesToCheck 失败", e);
        }

        Map<String, Map<String, Map<String, Integer>>> actualDataMap = new HashMap<>();
        List<String> queryErrors = collectActualData(citiesToCheck, actualDataMap);
        if (!queryErrors.isEmpty()) {
            log.info("[IntegrityCheckTask] 数据收集过程中有 {} 个时间范围的查询失败", queryErrors.size());
        }

        List<IntegrityCheckMismatch> allMismatches = compareDataIntegrity(actualDataMap, citiesToCheck);
        logMismatches(allMismatches);
        reportIntegrityMetrics(actualDataMap, citiesToCheck);

        log.info("[IntegrityCheckTask] GD冒泡完整性校验任务执行完成");
    }

    // ==================== 数据收集模块 ====================

    /**
     * 收集所有时间范围的实际数据，遍历配置中的每个时间范围和城市进行查询
     */
    private List<String> collectActualData(Map<String, Map<String, Map<String, Integer>>> citiesToCheck,
                                           Map<String, Map<String, Map<String, Integer>>> actualDataMap) {
        List<String> queryErrors = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Integer>>> entry : citiesToCheck.entrySet()) {
            String timeRange = entry.getKey();
            Map<String, Map<String, Integer>> citiesMap = entry.getValue();

            List<Integer> cityIds = convertCityNamesToIDs(citiesMap);
            if (cityIds.isEmpty()) {
                log.info("[IntegrityCheckTask] 时间范围 {} 下的城市无法转换为城市ID，跳过", timeRange);
                continue;
            }

            // 将时间范围转换为当天的日期时间范围
            TimeRangeDateTime dateTimeRange = convertTimeRangeToDateTime(timeRange);
            if (!dateTimeRange.isValid()) {
                log.info("[IntegrityCheckTask] 时间范围 {} 转换为日期时间失败，跳过", timeRange);
                queryErrors.add(timeRange);
                continue;
            }

            List<IntegrityCheckGroupResult> queryResults = integrityRepository.queryDataToCheck(
                    dateTimeRange.getStartTime(),
                    dateTimeRange.getEndTime(),
                    cityIds
            );

            organizeQueryResults(actualDataMap, timeRange, queryResults);
        }

        return queryErrors;
    }

    /**
     * 将城市名称映射转换为城市ID列表
     */
    private List<Integer> convertCityNamesToIDs(Map<String, Map<String, Integer>> citiesMap) {
        Map<String, Integer> cityMap = CityMap.getCityMap();
        List<Integer> cityIds = new ArrayList<>(citiesMap.size());
        for (String cityName : citiesMap.keySet()) {
            if (cityName == null || cityName.isEmpty()) {
                continue;
            }
            Integer cityId = cityMap.get(cityName);
            if (cityId != null && cityId > 0) {
                cityIds.add(cityId);
            }
        }
        return cityIds;
    }

    // ==================== 日志与指标模块 ====================

    /**
     * 记录完整性校验的不匹配情况，如果有不匹配则输出JSON格式的详细信息
     */
    private void logMismatches(List<IntegrityCheckMismatch> allMismatches) {
        if (allMismatches == null || allMismatches.isEmpty()) {
            log.info("[IntegrityCheckTask] 完整性校验通过，所有时间范围的数据都满足期望");
            return;
        }

        try {
            String mismatchesJson = objectMapper.writeValueAsString(allMismatches);
            log.info("[IntegrityCheckTask] 完整性校验发现 {} 个不匹配情况: {}", allMismatches.size(), mismatchesJson);
        } catch (JsonProcessingException e) {
            log.info("[IntegrityCheckTask] 完整性校验发现 {} 个不匹配情况，但序列化失败: {}", allMismatches.size(), e.getMessage());
        }
    }

    /**
     * 上报完整性校验的指标
     *
     * 指标1: integrity_missing_count - 缺失的问卷条数，维度：距离段、时间段、城市名称
     * 指标2: integrity_actual_count - 实际查询到的问卷条数，维度：距离段、时间段、城市名称
     */
    private void reportIntegrityMetrics(Map<String, Map<String, Map<String, Integer>>> actualDataMap,
                                        Map<String, Map<String, Map<String, Integer>>> citiesToCheck) {
        for (Map.Entry<String, Map<String, Map<String, Integer>>> timeEntry : citiesToCheck.entrySet()) {
            String timeRange = timeEntry.getKey();
            Map<String, Map<String, Integer>> expectedCitiesMap = timeEntry.getValue();
            if (expectedCitiesMap == null) {
                continue;
            }

            Map<String, Map<String, Integer>> actualCitiesMap =
                    actualDataMap.getOrDefault(timeRange, new HashMap<>());

            for (Map.Entry<String, Map<String, Integer>> cityEntry : expectedCitiesMap.entrySet()) {
                String cityName = cityEntry.getKey();
                Map<String, Integer> expectedDisRangeMap = cityEntry.getValue();
                if (expectedDisRangeMap == null) {
                    continue;
                }

                Map<String, Integer> actualDisRangeMap =
                        actualCitiesMap.getOrDefault(cityName, new HashMap<>());

                for (Map.Entry<String, Integer> disEntry : expectedDisRangeMap.entrySet()) {
                    String disRange = disEntry.getKey();
                    int expectedCount = disEntry.getValue();
                    if (disRange == null || disRange.isEmpty()) {
                        continue;
                    }

                    int actualCount = actualDisRangeMap.getOrDefault(disRange, 0);

                    // 上报实际数量指标
                    if (actualCount > 0) {
                        log.info("[IntegrityCheckTask] 上报实际数量指标: metric={}, dis_range={}, time_range={}, city_name={}, count={}",
                                INTEGRITY_ACTUAL_COUNT_METRIC, disRange, timeRange, cityName, actualCount);
                        if (metricsClient != null) {
                            metricsClient.count(INTEGRITY_ACTUAL_COUNT_METRIC, actualCount, Map.of());
                        }
                    }

                    if (actualCount < expectedCount) {
                        int missingCount = expectedCount - actualCount;
                        log.info("[IntegrityCheckTask] 上报缺失数量指标: metric={}, dis_range={}, time_range={}, city_name={}, missing_count={}, expected_count={}, actual_count={}",
                                INTEGRITY_MISSING_COUNT_METRIC, disRange, timeRange, cityName, missingCount, expectedCount, actualCount);
                        if (metricsClient != null) {
                            metricsClient.count(INTEGRITY_MISSING_COUNT_METRIC, missingCount, Map.of());
                        }
                    }
                }
            }
        }
    }

    // ==================== 时间处理模块 ====================

    /**
     * 从时间范围字符串中解析开始或结束时间，返回当天的对应时间点
     */
    private LocalDateTime parseTimeFromRange(String timeRange, boolean isStart) {
        if (timeRange == null || timeRange.isEmpty()) {
            throw new IllegalArgumentException("invalid time range: empty");
        }
        String[] parts = timeRange.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid time range format: " + timeRange);
        }

        String timeStr = isStart ? parts[0].trim() : parts[1].trim();
        try {
            LocalTime parsedTime = LocalTime.parse(timeStr, TIME_RANGE_FORMATTER);
            LocalDate today = LocalDate.now();
            return LocalDateTime.of(today, parsedTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("parse time failed: " + timeStr, e);
        }
    }

    /**
     * 将时间范围字符串（如"6:30-7:00"）转换为当天的日期时间范围（如"2025-01-15 06:30:00"和"2025-01-15 07:15:00"），endTime 向后偏移15分钟
     */
    private TimeRangeDateTime convertTimeRangeToDateTime(String timeRange) {
        try {
            LocalDateTime start = parseTimeFromRange(timeRange, true);
            LocalDateTime end = parseTimeFromRange(timeRange, false).plusMinutes(15);
            return new TimeRangeDateTime(start.format(DATE_TIME_FORMATTER), end.format(DATE_TIME_FORMATTER), true);
        } catch (IllegalArgumentException e) {
            log.info("[IntegrityCheckTask] convertTimeRangeToDateTime: 解析时间失败: {}, err={}", timeRange, e.getMessage());
            return TimeRangeDateTime.invalid();
        }
    }

    // ==================== 配置获取模块 ====================

    /**
     * 根据当前时间向前偏移16分钟的时间点从配置中获取待校验的城市和里程段配置，
     * 返回格式为 {时间范围: {城市: {里程段: 期望数量}}}
     */
    private Map<String, Map<String, Map<String, Integer>>> getCitiesToCheck() {
        Map<String, Map<String, Map<String, Integer>>> result = new HashMap<>();

        // 根据当前时间向前偏移16分钟
        LocalDateTime offsetTime = LocalDateTime.now().minusMinutes(16);

        Map<String, PatrolConfig> allConfigs = patrolConfigService.getAllPatrolConfigs();
        if (allConfigs == null || allConfigs.isEmpty()) {
            log.info("[IntegrityCheckTask] getCitiesToCheck: 未获取到任何配置");
            return result;
        }

        for (Map.Entry<String, PatrolConfig> entry : allConfigs.entrySet()) {
            String configKey = entry.getKey();
            PatrolConfig patrolConfig = entry.getValue();
            if (patrolConfig == null || patrolConfig.getCityList() == null || patrolConfig.getCityList().isEmpty()) {
                continue;
            }

            String matchedTimeRange = findMatchingTimeRange(offsetTime, patrolConfig.getPatrolDict());
            if (matchedTimeRange == null || matchedTimeRange.isEmpty()) {
                log.info("[IntegrityCheckTask] getCitiesToCheck: 配置 {} 中未找到匹配的时间范围，目标时间: {}", configKey,
                        offsetTime.format(TIME_RANGE_FORMATTER));
                continue;
            }

            // 检查时间范围的起点和终点（考虑 endTime 的15分钟偏移）是否都在当前时间之前
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime rangeStart;
            LocalDateTime rangeEnd;
            try {
                rangeStart = parseTimeFromRange(matchedTimeRange, true);
                rangeEnd = parseTimeFromRange(matchedTimeRange, false).plusMinutes(15);
            } catch (IllegalArgumentException e) {
                log.info("[IntegrityCheckTask] getCitiesToCheck: 配置 {} 中时间范围 {} 解析失败，跳过", configKey, matchedTimeRange);
                continue;
            }

            if (!rangeStart.isBefore(now) || !rangeEnd.isBefore(now)) {
                log.info(
                        "[IntegrityCheckTask] getCitiesToCheck: 配置 {} 中时间范围 {} 的起点或终点在当前时间之后，跳过。startTime: {}, endTime: {}, now: {}",
                        configKey,
                        matchedTimeRange,
                        rangeStart.format(DATE_TIME_FORMATTER),
                        rangeEnd.format(DATE_TIME_FORMATTER),
                        now.format(DATE_TIME_FORMATTER)
                );
                continue;
            }

            Map<String, Integer> disRangeConfig = patrolConfig.getPatrolDict().get(matchedTimeRange);
            if (disRangeConfig == null || disRangeConfig.isEmpty()) {
                log.info("[IntegrityCheckTask] getCitiesToCheck: 配置 {} 中时间范围 {} 没有里程段配置", configKey, matchedTimeRange);
                continue;
            }

            result.computeIfAbsent(matchedTimeRange, k -> new HashMap<>());

            for (String city : patrolConfig.getCityList()) {
                if (city == null || city.isEmpty()) {
                    continue;
                }

                Map<String, Integer> cityDisRangeMap =
                        result.get(matchedTimeRange).computeIfAbsent(city, k -> new HashMap<>());

                for (Map.Entry<String, Integer> disEntry : disRangeConfig.entrySet()) {
                    String disRange = disEntry.getKey();
                    Integer count = disEntry.getValue();
                    if (disRange != null && !disRange.isEmpty() && count != null && count > 0) {
                        cityDisRangeMap.put(disRange, count);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 在配置字典中查找包含目标时间点的时间范围，匹配规则为 (configStart, configEnd]
     */
    private String findMatchingTimeRange(LocalDateTime targetTime,
                                         Map<String, Map<String, Integer>> patrolDict) {
        if (patrolDict == null || patrolDict.isEmpty()) {
            return "";
        }

        LocalTime targetTimeOfDay = targetTime.toLocalTime();
        for (String configTimeRange : patrolDict.keySet()) {
            LocalDateTime configStart;
            LocalDateTime configEnd;
            try {
                configStart = parseTimeFromRange(configTimeRange, true);
                configEnd = parseTimeFromRange(configTimeRange, false);
            } catch (IllegalArgumentException e) {
                continue;
            }

            LocalTime startOfDay = configStart.toLocalTime();
            LocalTime endOfDay = configEnd.toLocalTime();

            // 匹配规则：不包含起点，包含终点 (configStart, configEnd]
            if (targetTimeOfDay.isAfter(startOfDay) && !targetTimeOfDay.isAfter(endOfDay)) {
                return configTimeRange;
            }
        }

        return "";
    }

    // ==================== 数据组织与对比模块 ====================

    /**
     * 将查询结果按时间范围、城市、里程段组织到 actualDataMap 中
     */
    private void organizeQueryResults(Map<String, Map<String, Map<String, Integer>>> actualDataMap,
                                      String timeRange,
                                      List<IntegrityCheckGroupResult> queryResults) {
        actualDataMap.computeIfAbsent(timeRange, k -> new HashMap<>());

        if (queryResults == null) {
            return;
        }

        for (IntegrityCheckGroupResult result : queryResults) {
            if (result == null
                    || result.getCityName() == null || result.getCityName().isEmpty()
                    || result.getDisRange() == null || result.getDisRange().isEmpty()) {
                continue;
            }

            Map<String, Map<String, Integer>> cityMap = actualDataMap.get(timeRange);
            Map<String, Integer> disRangeMap =
                    cityMap.computeIfAbsent(result.getCityName(), k -> new HashMap<>());
            disRangeMap.put(result.getDisRange(), result.getCount());
        }
    }

    /**
     * 对比实际数据和期望配置，返回所有不满足期望的情况列表
     */
    private List<IntegrityCheckMismatch> compareDataIntegrity(
            Map<String, Map<String, Map<String, Integer>>> actualDataMap,
            Map<String, Map<String, Map<String, Integer>>> citiesToCheck) {

        List<IntegrityCheckMismatch> mismatches = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Integer>>> timeEntry : citiesToCheck.entrySet()) {
            String timeRange = timeEntry.getKey();
            Map<String, Map<String, Integer>> expectedCitiesMap = timeEntry.getValue();
            if (expectedCitiesMap == null) {
                continue;
            }

            Map<String, Map<String, Integer>> actualCitiesMap =
                    actualDataMap.getOrDefault(timeRange, new HashMap<>());

            for (Map.Entry<String, Map<String, Integer>> cityEntry : expectedCitiesMap.entrySet()) {
                String cityName = cityEntry.getKey();
                Map<String, Integer> expectedDisRangeMap = cityEntry.getValue();
                if (expectedDisRangeMap == null) {
                    continue;
                }

                Map<String, Integer> actualDisRangeMap =
                        actualCitiesMap.getOrDefault(cityName, new HashMap<>());

                for (Map.Entry<String, Integer> disEntry : expectedDisRangeMap.entrySet()) {
                    String disRange = disEntry.getKey();
                    int expectedCount = disEntry.getValue();
                    if (disRange == null || disRange.isEmpty() || expectedCount <= 0) {
                        continue;
                    }

                    int actualCount = actualDisRangeMap.getOrDefault(disRange, 0);
                    if (actualCount < expectedCount) {
                        IntegrityCheckMismatch mismatch = new IntegrityCheckMismatch();
                        mismatch.setTimeRange(timeRange);
                        mismatch.setCityName(cityName);
                        mismatch.setDisRange(disRange);
                        mismatch.setExpected(expectedCount);
                        mismatch.setActual(actualCount);
                        mismatch.setMissing(expectedCount - actualCount);
                        mismatches.add(mismatch);
                    }
                }
            }
        }

        return mismatches;
    }

    // ==================== 辅助类型 ====================

    @Data
    private static class IntegrityCheckMismatch {
        private String timeRange;
        private String cityName;
        private String disRange;
        private int expected;
        private int actual;
        private int missing;
    }

    @Data
    private static class TimeRangeDateTime {
        private final String startTime;
        private final String endTime;
        private final boolean valid;

        static TimeRangeDateTime invalid() {
            return new TimeRangeDateTime("", "", false);
        }
    }
}

