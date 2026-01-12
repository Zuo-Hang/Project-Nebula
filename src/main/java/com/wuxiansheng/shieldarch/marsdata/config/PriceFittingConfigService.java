package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 特价车价格拟合配置服务
 */
@Slf4j
@Component
public class PriceFittingConfigService {

    @Autowired
    private AppConfigService appConfigService;

    private static final String PRICE_FITTING_CONF = AppConfigService.PRICE_FITTING_CONF;
    private static final String PRICE_FITTING_OPENED_CITIES_KEY = "price_fitting_opened_cities";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取特价车价格拟合任务已开城的城市列表
     * 配置名称: PRICE_FITTING_CONF，配置 key: price_fitting_opened_cities
     * 值为 JSON 数组格式，如：["北京市","上海市","广州市"]
     * 如果配置不存在或解析失败，返回空数组
     */
    public List<String> getPriceFittingOpenedCities() {
        try {
            Map<String, String> configMap = appConfigService.getConfig(PRICE_FITTING_CONF);
            if (configMap == null || configMap.isEmpty()) {
                log.warn("[PriceFittingConfigService] 获取配置失败，namespace={}，返回空列表", PRICE_FITTING_CONF);
                return Collections.emptyList();
            }

            String valStr = configMap.getOrDefault(PRICE_FITTING_OPENED_CITIES_KEY, "");
            if (StringUtils.isBlank(valStr)) {
                log.info("[PriceFittingConfigService] price_fitting_opened_cities 为空，返回空列表");
                return Collections.emptyList();
            }

            try {
                List<String> cities = objectMapper.readValue(valStr, new TypeReference<List<String>>() {});
                log.info("[PriceFittingConfigService] 成功加载已开城城市列表，共 {} 个城市: {}", cities.size(), cities);
                return cities != null ? cities : Collections.emptyList();
            } catch (Exception e) {
                log.warn("[PriceFittingConfigService] 解析 price_fitting_opened_cities 失败，使用空列表, raw={}", valStr, e);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("[PriceFittingConfigService] getPriceFittingOpenedCities 加载配置异常，返回空列表", e);
            return Collections.emptyList();
        }
    }
}
