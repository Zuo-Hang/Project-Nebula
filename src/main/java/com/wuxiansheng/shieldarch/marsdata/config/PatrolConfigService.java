package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检配置服务
 *
 * Go 侧逻辑：
 * - namespace: QUALITY_MONITOR_CONF
 * - key: city_patrol_conf -> JSON 数组，例如 ["GP_ZJ_CONF","GP_ZJ_JX_CONF","GP_CONF","DP_CONF"]
 * - 每个 key 对应一个 JSON 字符串，反序列化为 PatrolConfig
 */
@Slf4j
@Component
public class PatrolConfigService {

    @Autowired
    private AppConfigService appConfigService;

    /**
     */
    private static final String QUALITY_MONITOR_CONF = AppConfigService.QUALITY_MONITOR_CONF;

    /**
     * 存放配置 key 列表的字段名：city_patrol_conf
     */
    private static final String CITY_PATROL_CONF_KEY = "city_patrol_conf";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取所有城市巡检配置
     *
     * @return key 为配置名称，value 为对应的 PatrolConfig
     */
    public Map<String, PatrolConfig> getAllPatrolConfigs() {
        try {
            Map<String, String> configMap = appConfigService.getConfig(QUALITY_MONITOR_CONF);
            if (configMap == null || configMap.isEmpty()) {
                log.warn("[PatrolConfigService] 获取配置失败，namespace={}，返回空配置", QUALITY_MONITOR_CONF);
                return Collections.emptyMap();
            }

            // 1. 读取 city_patrol_conf，得到配置 key 列表
            String confKeysJson = configMap.getOrDefault(CITY_PATROL_CONF_KEY, "");
            if (StringUtils.isBlank(confKeysJson)) {
                log.info("[PatrolConfigService] city_patrol_conf 为空，返回空配置");
                return Collections.emptyMap();
            }

            List<String> confKeys = parseConfigKeys(confKeysJson);
            if (confKeys.isEmpty()) {
                log.info("[PatrolConfigService] 解析 city_patrol_conf 结果为空，返回空配置，raw={}", confKeysJson);
                return Collections.emptyMap();
            }

            // 2. 对每个 key 读取并解析对应的 PatrolConfig
            Map<String, PatrolConfig> result = new HashMap<>();
            for (String key : confKeys) {
                if (StringUtils.isBlank(key)) {
                    continue;
                }
                PatrolConfig patrolConfig = loadPatrolConfig(configMap, key);
                result.put(key, patrolConfig);
            }

            log.info("[PatrolConfigService] 成功加载巡检配置，共 {} 个 key: {}", result.size(), confKeys);
            return result;
        } catch (Exception e) {
            log.warn("[PatrolConfigService] getAllPatrolConfigs 加载配置异常，返回空配置", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 解析 city_patrol_conf JSON 数组
     */
    private List<String> parseConfigKeys(String confKeysJson) {
        try {
            return objectMapper.readValue(confKeysJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("[PatrolConfigService] 解析 city_patrol_conf 失败，使用空列表, raw={}", confKeysJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * 读取并解析单个 PatrolConfig
     */
    private PatrolConfig loadPatrolConfig(Map<String, String> configMap, String key) {
        String valStr = configMap.getOrDefault(key, "");
        if (StringUtils.isBlank(valStr)) {
            log.info("[PatrolConfigService] 巡检配置 key={} 不存在或为空，使用空配置", key);
            return new PatrolConfig();
        }

        try {
            PatrolConfig config = objectMapper.readValue(valStr, PatrolConfig.class);
            if (config.getPatrolDict() == null) {
                config.setPatrolDict(new HashMap<>());
            }
            if (config.getCityList() == null) {
                config.setCityList(Collections.emptyList());
            }
            return config;
        } catch (Exception e) {
            log.warn("[PatrolConfigService] 解析巡检配置失败, key={} raw={}, 使用空配置", key, valStr, e);
            return new PatrolConfig();
        }
    }
}



