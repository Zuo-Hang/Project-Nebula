package com.wuxiansheng.shieldarch.marsdata.scheduler.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.PriceFittingConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.DufeClient;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.marsdata.scheduler.LockedTask;
import com.wuxiansheng.shieldarch.marsdata.scheduler.repository.EconomyBubble;
import com.wuxiansheng.shieldarch.marsdata.scheduler.repository.MysqlRow;
import com.wuxiansheng.shieldarch.marsdata.scheduler.repository.PriceFittingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 特价车价格拟合任务
 */
@Slf4j
@Component
public class PriceFittingTask implements LockedTask {

    private static final String CRON_EXPRESSION = "*/5 * * * * *";
    private static final String LOCK_KEY = "price_fitting_task_lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private static final String PRICE_FITTING_SUPPLIER_METRIC = "price_fitting_supplier_count";
    private static final String PRICE_FITTING_MATCHED_METRIC = "price_fitting_matched_count";
    private static final String PRICE_FITTING_MISSING_RESPONSE_RATE = "price_fitting_missing_response_rate";

    // Dufe 模板ID
    private static final String DUFE_TEMPLATE_ID = "dufe-b835c16d-e026-4986-82d2-15a202a8a058";
    private static final String DUFE_FEATURE_KEY = "o_supplier_rate";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PriceFittingRepository repository;
    private final PriceFittingConfigService configService;
    private final DufeClient dufeClient;
    private final MetricsClientAdapter metricsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PriceFittingTask(PriceFittingRepository repository,
                           PriceFittingConfigService configService,
                           DufeClient dufeClient,
                           MetricsClientAdapter metricsClient) {
        this.repository = repository;
        this.configService = configService;
        this.dufeClient = dufeClient;
        this.metricsClient = metricsClient;
    }

    @Override
    public String getName() {
        return "PriceFittingTask";
    }

    @Override
    public void execute() throws Exception {
        log.info("[PriceFittingTask] 执行价格拟合任务: {}", getName());

        // 1. 获取已开城城市列表
        List<String> openedCityList = configService.getPriceFittingOpenedCities();
        log.info("[PriceFittingTask] openedCityNames size={}, cities={}", openedCityList.size(), openedCityList);

        // 2. 计算48小时前的时间
        String fortyEightHoursAgo = LocalDateTime.now().minusHours(48).format(DATE_TIME_FORMATTER);

        // 3. 读取未拟合的特价车数据
        List<MysqlRow> unfittedSpecialPrices = repository.getUnfittedSpecialPrices(fortyEightHoursAgo, openedCityList);
        if (unfittedSpecialPrices.isEmpty()) {
            return;
        }

        // 按 EstimateId 分组特价车数据
        Map<String, List<MysqlRow>> estimateId2SpecialPricesList = unfittedSpecialPrices.stream()
                .collect(Collectors.groupingBy(MysqlRow::getEstimateId));
        Set<String> estimateIDsSet = new HashSet<>(estimateId2SpecialPricesList.keySet());

        // 打印分组统计信息
        Map<String, Integer> estimateIDCounts = estimateId2SpecialPricesList.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
        try {
            String countsJson = objectMapper.writeValueAsString(estimateIDCounts);
            log.info("[PriceFittingTask] 按 EstimateId 分组后，共 {} 个 estimate_id，每个 estimate_id 的特价车数量: {}",
                    estimateId2SpecialPricesList.size(), countsJson);
        } catch (Exception e) {
            log.warn("[PriceFittingTask] 序列化分组统计失败", e);
        }

        List<String> estimateIDs = new ArrayList<>(estimateIDsSet);

        // 4. 获取经济型数据
        List<EconomyBubble> economyBubbles = repository.getEconomyBubbles(estimateIDs, openedCityList);

        // 按 EstimateId 分组经济型数据
        Map<String, List<EconomyBubble>> estimateId2EconomyBubblesList = economyBubbles.stream()
                .collect(Collectors.groupingBy(EconomyBubble::getEstimateId));
        Set<String> economyEstimateIDsSet = new HashSet<>(estimateId2EconomyBubblesList.keySet());

        // 打印分组统计信息
        Map<String, Integer> economyEstimateIDCounts = estimateId2EconomyBubblesList.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
        try {
            String countsJson = objectMapper.writeValueAsString(economyEstimateIDCounts);
            log.info("[PriceFittingTask] 按 EstimateId 分组经济型数据后，共 {} 个 estimate_id，每个 estimate_id 的经济型数据数量: {}",
                    estimateId2EconomyBubblesList.size(), countsJson);
        } catch (Exception e) {
            log.warn("[PriceFittingTask] 序列化经济型分组统计失败", e);
        }

        // 收集所有唯一的 (city_name, partner_name) 组合
        Set<CityPartnerKey> uniqueKeys = collectUniqueCityPartnerKeys(economyBubbles, unfittedSpecialPrices);

        // 5. 获取应答概率
        Map<String, Double> responseRateMap = getResponseRates(uniqueKeys);

        // 6. 对特价车价格进行拟合
        List<MysqlRow> fittedResults = fitPricesForEstimateIDs(
                estimateIDsSet, economyEstimateIDsSet,
                estimateId2SpecialPricesList, estimateId2EconomyBubblesList,
                responseRateMap);

        // 7. 保存拟合结果
        if (!fittedResults.isEmpty()) {
            log.info("[PriceFittingTask] 共生成 {} 条拟合结果，开始写入数据库", fittedResults.size());
            repository.saveFittedResults(fittedResults);
            log.info("[PriceFittingTask] 成功保存 {} 条拟合结果", fittedResults.size());
        }
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

    // ==================== 数据收集模块 ====================

    /**
     * 城市和供应商的组合键
     */
    private static class CityPartnerKey {
        String cityName;
        String partnerName;

        CityPartnerKey(String cityName, String partnerName) {
            this.cityName = cityName;
            this.partnerName = partnerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CityPartnerKey that = (CityPartnerKey) o;
            return Objects.equals(cityName, that.cityName) && Objects.equals(partnerName, that.partnerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cityName, partnerName);
        }
    }

    /**
     * 从经济型数据和特价车数据中收集所有唯一的 (city_name, partner_name) 组合
     */
    private Set<CityPartnerKey> collectUniqueCityPartnerKeys(List<EconomyBubble> economyBubbles,
                                                             List<MysqlRow> specialPrices) {
        Set<CityPartnerKey> uniqueKeys = new HashSet<>();

        // 从经济型数据中收集
        for (EconomyBubble bubble : economyBubbles) {
            if (bubble.getCityName() != null && !bubble.getCityName().isEmpty() &&
                bubble.getPartnerName() != null && !bubble.getPartnerName().isEmpty()) {
                uniqueKeys.add(new CityPartnerKey(bubble.getCityName(), bubble.getPartnerName()));
            }
        }

        // 从特价车数据中收集
        for (MysqlRow sp : specialPrices) {
            if (sp != null && sp.getCityName() != null && !sp.getCityName().isEmpty() &&
                sp.getPartnerName() != null && !sp.getPartnerName().isEmpty()) {
                uniqueKeys.add(new CityPartnerKey(sp.getCityName(), sp.getPartnerName()));
            }
        }

        return uniqueKeys;
    }

    // ==================== 应答概率模块 ====================

    /**
     * 使用 dufe 查询应答概率
     */
    private Map<String, Double> getResponseRates(Set<CityPartnerKey> uniqueKeys) {
        Map<String, Double> responseRateMap = new HashMap<>();

        if (!dufeClient.isAvailable()) {
            log.info("[PriceFittingTask] getResponseRates: dufe 服务不可用，使用默认应答概率(warn)");
            return responseRateMap;
        }

        int totalQueries = 0;
        int successCount = 0;
        int failCount = 0;
        List<Map<String, String>> missingFeatureRequests = new ArrayList<>();

        for (CityPartnerKey key : uniqueKeys) {
            totalQueries++;

            Map<String, String> params = new HashMap<>();
            params.put("city_name", key.cityName);
            params.put("partner_name", key.partnerName);

            try {
                Map<String, String> features = dufeClient.getTemplateFeature(DUFE_TEMPLATE_ID, params);
                if (features == null || features.isEmpty()) {
                    failCount++;
                    log.info("[PriceFittingTask] getResponseRates: dufe 返回为空(warn)[{}/{}]: city_name={}, partner_name={}",
                            totalQueries, uniqueKeys.size(), key.cityName, key.partnerName);
                    continue;
                }

                String responseRateStr = features.get(DUFE_FEATURE_KEY);
                if (responseRateStr == null || responseRateStr.isEmpty()) {
                    Map<String, String> missing = new HashMap<>();
                    missing.put("city_name", key.cityName);
                    missing.put("partner_name", key.partnerName);
                    missingFeatureRequests.add(missing);
                    continue;
                }

                try {
                    double responseRate = Double.parseDouble(responseRateStr);
                    String mapKey = key.cityName + "_" + key.partnerName;
                    responseRateMap.put(mapKey, responseRate);
                    successCount++;
                } catch (NumberFormatException e) {
                    failCount++;
                    log.info("[PriceFittingTask] getResponseRates: 解析应答概率失败(warn)[{}/{}]: city_name={}, partner_name={}, value={}, err={}",
                            totalQueries, uniqueKeys.size(), key.cityName, key.partnerName, responseRateStr, e.getMessage());
                }
            } catch (Exception e) {
                failCount++;
                log.info("[PriceFittingTask] getResponseRates: dufe 查询应答概率失败(warn)[{}/{}]: city_name={}, partner_name={}, err={}",
                        totalQueries, uniqueKeys.size(), key.cityName, key.partnerName, e.getMessage());
            }
        }

        // 打印最终统计信息
        try {
            String successfulRatesJson = objectMapper.writeValueAsString(responseRateMap);
            String missingInfo = "";
            if (!missingFeatureRequests.isEmpty()) {
                String missingJson = objectMapper.writeValueAsString(missingFeatureRequests);
                missingInfo = ", 缺少 o_supplier_rate 字段的请求共 " + missingFeatureRequests.size() + " 个: " + missingJson;
            }
            log.info("[PriceFittingTask] getResponseRates: 完成，共调用 dufe {} 次，成功获取 {} 条应答概率，失败 {} 次，成功获取的应答概率列表: {}{}",
                    totalQueries, successCount, failCount, successfulRatesJson, missingInfo);
        } catch (Exception e) {
            log.warn("[PriceFittingTask] 序列化应答概率统计失败", e);
        }

        return responseRateMap;
    }

    // ==================== 拟合计算模块 ====================

    /**
     * 对每个 estimate_id 进行价格拟合处理
     */
    private List<MysqlRow> fitPricesForEstimateIDs(
            Set<String> estimateIDsSet,
            Set<String> economyEstimateIDsSet,
            Map<String, List<MysqlRow>> estimateId2SpecialPricesList,
            Map<String, List<EconomyBubble>> estimateId2EconomyBubblesList,
            Map<String, Double> responseRateMap) {

        // 1. 对比 estimateIDsSet 和 economyEstimateIDsSet，得到匹配和未匹配的列表
        List<String> matchedEstimateIDs = new ArrayList<>();
        List<String> unmatchedEstimateIDs = new ArrayList<>();

        for (String estimateID : estimateIDsSet) {
            if (economyEstimateIDsSet.contains(estimateID)) {
                matchedEstimateIDs.add(estimateID);
            } else {
                unmatchedEstimateIDs.add(estimateID);
            }
        }

        Collections.sort(matchedEstimateIDs);
        Collections.sort(unmatchedEstimateIDs);

        try {
            String matchedInfo = matchedEstimateIDs.isEmpty() ? "" :
                    ", 匹配到的 estimate_id " + matchedEstimateIDs.size() + " 个: " + objectMapper.writeValueAsString(matchedEstimateIDs);
            String unmatchedInfo = unmatchedEstimateIDs.isEmpty() ? "" :
                    ", 未匹配到的 estimate_id " + unmatchedEstimateIDs.size() + " 个: " + objectMapper.writeValueAsString(unmatchedEstimateIDs);
            log.info("[PriceFittingTask] fitPricesForEstimateIDs: estimate_id 匹配情况{}{}", matchedInfo, unmatchedInfo);
        } catch (Exception e) {
            log.warn("[PriceFittingTask] 序列化匹配情况失败", e);
        }

        List<MysqlRow> fittedResults = new ArrayList<>();

        // 2. 遍历匹配到的 EstimateIDs
        for (String estimateID : matchedEstimateIDs) {
            List<MysqlRow> relatedSpecialPrices = estimateId2SpecialPricesList.get(estimateID);
            List<EconomyBubble> economyBubbles = estimateId2EconomyBubblesList.get(estimateID);

            if (relatedSpecialPrices == null || relatedSpecialPrices.isEmpty() ||
                economyBubbles == null || economyBubbles.isEmpty()) {
                continue;
            }

            MysqlRow specialPrice = relatedSpecialPrices.get(0);

            // 计算经济型 top4 价格
            List<Double> economyPrices = economyBubbles.stream()
                    .map(EconomyBubble::getEstPay)
                    .filter(Objects::nonNull)
                    .sorted(Collections.reverseOrder())
                    .collect(Collectors.toList());

            if (economyPrices.isEmpty()) {
                continue;
            }

            double top4Price = economyPrices.size() >= 4 ? economyPrices.get(3) : economyPrices.get(0);

            // 计算拟合结果
            MysqlRow fittedResult = calculateFitting(specialPrice, relatedSpecialPrices, economyBubbles, top4Price, responseRateMap);
            if (fittedResult != null) {
                fittedResults.add(fittedResult);
            }
        }

        return fittedResults;
    }

    /**
     * 计算价格拟合
     */
    private MysqlRow calculateFitting(MysqlRow specialPrice,
                                    List<MysqlRow> relatedSpecialPrices,
                                    List<EconomyBubble> economyBubbles,
                                    double top4Price,
                                    Map<String, Double> responseRateMap) {

        // 1. 筛选价格小于等于经济型top4的特价车
        List<MysqlRow> validSpecialPrices = relatedSpecialPrices.stream()
                .filter(sp -> sp.getCapPrice() != null && sp.getCapPrice() <= top4Price)
                .collect(Collectors.toList());

        if (validSpecialPrices.isEmpty()) {
            log.info("[PriceFittingTask] estimate_id={} 没有价格小于等于经济型top4的特价车，生成虚拟拟合结果(warn), top4_price={:.2f}",
                    specialPrice.getEstimateId(), String.format("%.2f", top4Price));
            return createVirtualFittedResult(specialPrice);
        }

        // 2. 构建供应商到经济型实付的映射
        Map<String, Double> partnerToEconomyPay = new HashMap<>();
        for (EconomyBubble bubble : economyBubbles) {
            if (bubble.getEstPay() != null && bubble.getEstPay() <= top4Price) {
                String partner = bubble.getPartnerName();
                Double existing = partnerToEconomyPay.get(partner);
                if (existing == null || bubble.getEstPay() < existing) {
                    partnerToEconomyPay.put(partner, bubble.getEstPay());
                }
            }
        }

        // 3. 计算应答占比（缺失则丢弃该供应商）
        Map<String, Double> partnerResponseRates = new HashMap<>();
        double totalRate = 0.0;
        List<MysqlRow> filteredSpecialPrices = new ArrayList<>();
        List<String> missingPartners = new ArrayList<>();
        String cityName = null;

        for (MysqlRow sp : validSpecialPrices) {
            if (cityName == null) {
                cityName = sp.getCityName();
            }
            String key = sp.getCityName() + "_" + sp.getPartnerName();
            Double rate = responseRateMap.get(key);
            if (rate == null || rate <= 0) {
                missingPartners.add(sp.getPartnerName());
                continue;
            }
            partnerResponseRates.put(sp.getPartnerName(), rate);
            totalRate += rate;
            filteredSpecialPrices.add(sp);
        }

        if (!missingPartners.isEmpty()) {
            log.info("[PriceFittingTask] estimate_id={}, city_name={} 缺少以下供应商的应答率，跳过这些记录(warn): {}",
                    specialPrice.getEstimateId(), cityName, missingPartners);
            if (metricsClient != null) {
                metricsClient.increment(PRICE_FITTING_MISSING_RESPONSE_RATE, Map.of());
            }
        }

        if (filteredSpecialPrices.isEmpty()) {
            log.info("[PriceFittingTask] estimate_id={} 所有供应商均缺少应答率，生成虚拟拟合结果(warn)", specialPrice.getEstimateId());
            return createVirtualFittedResult(specialPrice);
        }

        // 归一化应答占比
        if (totalRate > 0) {
            for (String partner : partnerResponseRates.keySet()) {
                partnerResponseRates.put(partner, partnerResponseRates.get(partner) / totalRate);
            }
        }

        // 4. 特价算法：SUMPRODUCT
        double specialPriceFitted = 0.0;
        for (MysqlRow sp : filteredSpecialPrices) {
            Double rate = partnerResponseRates.get(sp.getPartnerName());
            if (rate != null && sp.getCapPrice() != null) {
                specialPriceFitted += sp.getCapPrice() * rate;
            }
        }

        // 5. 原价算法：SUMPRODUCT
        double originalPriceFitted = 0.0;
        List<String> missingEconomyPartners = new ArrayList<>();
        for (MysqlRow sp : filteredSpecialPrices) {
            Double economyPay = partnerToEconomyPay.get(sp.getPartnerName());
            if (economyPay == null) {
                missingEconomyPartners.add(sp.getPartnerName());
                economyPay = sp.getCapPrice();
            }
            Double rate = partnerResponseRates.get(sp.getPartnerName());
            if (rate != null && economyPay != null) {
                originalPriceFitted += economyPay * rate;
            }
        }

        // 6. 构建拟合结果
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        MysqlRow fittedResult = new MysqlRow();
        fittedResult.setEstimateId(specialPrice.getEstimateId());
        fittedResult.setBubbleImageUrl(specialPrice.getBubbleImageUrl());
        fittedResult.setPartnerName("特价车拟合");
        fittedResult.setCapPrice(specialPriceFitted);
        fittedResult.setReducePrice(originalPriceFitted - specialPriceFitted);
        fittedResult.setCarType(specialPrice.getCarType());
        fittedResult.setCityId(specialPrice.getCityId());
        fittedResult.setCityName(specialPrice.getCityName());
        fittedResult.setCreateTime(now);
        fittedResult.setUpdateTime(now);
        fittedResult.setType(1); // 拟合结果

        return fittedResult;
    }

    /**
     * 创建虚拟拟合结果（cap_price=0, reduce_price=0）
     */
    private MysqlRow createVirtualFittedResult(MysqlRow specialPrice) {
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        MysqlRow result = new MysqlRow();
        result.setEstimateId(specialPrice.getEstimateId());
        result.setBubbleImageUrl(specialPrice.getBubbleImageUrl());
        result.setPartnerName("特价车拟合");
        result.setCapPrice(0.0);
        result.setReducePrice(0.0);
        result.setCarType(specialPrice.getCarType());
        result.setCityId(specialPrice.getCityId());
        result.setCityName(specialPrice.getCityName());
        result.setCreateTime(now);
        result.setUpdateTime(now);
        result.setType(1); // 拟合结果
        return result;
    }
}
