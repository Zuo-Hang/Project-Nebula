package com.wuxiansheng.shieldarch.orchestrator.config;

import java.util.Map;

/**
 * 配置服务接口
 * 
 * 抽象配置管理功能，支持多种实现（Nacos 等）
 * 用于统一配置管理接口
 * 
 * 注意：为了避免与 Nacos 的 ConfigService 冲突，使用 AppConfigService 作为接口名
 */
public interface AppConfigService {
    
    /**
     * 配置命名空间常量
     */
    String OCR_LLM_CONF = "OCR_LLM_CONF";
    String PRICE_FITTING_CONF = "PRICE_FITTING_CONF";
    String QUALITY_MONITOR_CONF = "QUALITY_MONITOR_CONF";
    String OCR_BUSINESS_CONF = "OCR_BUSINESS_CONF";
    
    /**
     * 获取配置命名空间常量（静态方法，方便访问）
     */
    static String getOcrLlmConf() {
        return OCR_LLM_CONF;
    }
    
    static String getPriceFittingConf() {
        return PRICE_FITTING_CONF;
    }
    
    static String getQualityMonitorConf() {
        return QUALITY_MONITOR_CONF;
    }
    
    static String getOcrBusinessConf() {
        return OCR_BUSINESS_CONF;
    }
    
    /**
     * 获取配置
     * 
     * @param namespace 配置命名空间
     * @return 配置Map，key为配置项名称，value为配置值
     */
    Map<String, String> getConfig(String namespace);
    
    /**
     * 获取单个配置项的值
     * 
     * @param namespace 配置命名空间
     * @param key 配置项key
     * @param defaultValue 默认值
     * @return 配置值，如果不存在则返回默认值
     */
    String getProperty(String namespace, String key, String defaultValue);
    
    /**
     * 获取单个配置项的值（无默认值）
     * 
     * @param namespace 配置命名空间
     * @param key 配置项key
     * @return 配置值，如果不存在则返回null
     */
    String getProperty(String namespace, String key);
    
    /**
     * 检查配置项是否存在
     * 
     * @param namespace 配置命名空间
     * @param key 配置项key
     * @return 是否存在
     */
    boolean hasProperty(String namespace, String key);
}

