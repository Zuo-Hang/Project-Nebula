package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务配置服务
 */
@Slf4j
@Service
public class BusinessConfigService {
    
    @Autowired
    private AppConfigService appConfigService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 所有业务名称列表
     */
    private final List<String> allBusinessNames = new ArrayList<>();
    
    /**
     * 业务配置缓存（每次从配置中心获取）
     */
    private Map<String, BusinessConf> businessConfCache = new ConcurrentHashMap<>();
    
    /**
     * 注册所有业务名称
     * 
     * @param businessNames 业务名称列表
     */
    public void registerAllBusiness(List<String> businessNames) {
        allBusinessNames.clear();
        allBusinessNames.addAll(businessNames);
        log.info("注册所有业务名称: {}", allBusinessNames);
    }
    
    /**
     * 获取所有业务配置（从配置中心获取并解析）
     * 
     * @return 业务配置Map，key为业务名称，value为业务配置
     */
    public Map<String, BusinessConf> getAllBusinessConf() {
        Map<String, BusinessConf> result = new HashMap<>();
        
        // 从配置中心获取配置
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_BUSINESS_CONF);
        
        // 如果配置获取失败，使用默认配置
        if (params.isEmpty()) {
            log.warn("从配置中心获取业务配置失败，使用默认配置");
            params = getDefaultBusinessConfs();
        }
        
        // 解析每个业务的配置
        for (String businessName : allBusinessNames) {
            String confName = "business_" + businessName;
            String confStr = params.get(confName);
            
            if (confStr == null || confStr.isEmpty()) {
                log.debug("业务配置不存在: {}", confName);
                continue;
            }
            
            try {
                BusinessConf businessConf = objectMapper.readValue(confStr, BusinessConf.class);
                result.put(businessName, businessConf);
            } catch (Exception e) {
                log.error("解析业务配置失败: businessName={}, error={}", businessName, e.getMessage(), e);
            }
        }
        
        // 更新缓存
        businessConfCache = result;
        
        return result;
    }
    
    /**
     * 获取业务配置
     * 
     * @param businessName 业务名称
     * @return 业务配置，如果不存在则返回null
     */
    public BusinessConf getBusinessConf(String businessName) {
        Map<String, BusinessConf> confsMap = getAllBusinessConf();
        BusinessConf conf = confsMap.get(businessName);
        
        if (conf == null) {
            log.error("使用未知的业务: {}", businessName);
        }
        
        return conf;
    }
    
    /**
     * 根据sourceUniqueId获取业务名称列表
     * 
     * @param sourceUniqueId 源唯一ID
     * @return 业务名称列表
     */
    public List<String> getSourceBusinessNames(String sourceUniqueId) {
        List<String> result = new ArrayList<>();
        
        Map<String, BusinessConf> allConfs = getAllBusinessConf();
        
        for (BusinessConf businessConf : allConfs.values()) {
            // 跳过未启用的业务
            if (!businessConf.isEnable()) {
                continue;
            }
            
            // 检查sources中是否包含该sourceUniqueId
            if (businessConf.getSources() != null) {
                for (BusinessSourceConf sourceConf : businessConf.getSources()) {
                    if (sourceUniqueId.equals(sourceConf.getUniqueId())) {
                        result.add(businessConf.getName());
                        break; // 找到后跳出内层循环
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取默认业务配置
     */
    private Map<String, String> getDefaultBusinessConfs() {
        Map<String, String> defaultConfs = new HashMap<>();
        
        // b_saas
        defaultConfs.put("business_b_saas", """
            {
                "name": "b_saas",
                "enable": true,
                "max_concurrent": 30,
                "sources": [
                    {
                        "unique_id": "B-SAAS",
                        "level": 1,
                        "is_test": false
                    }
                ]
            }""");
        
        // gd_bubble
        defaultConfs.put("business_gd_bubble", """
            {
                "name": "gd_bubble",
                "enable": false,
                "max_concurrent": 0,
                "sources": [
                    {
                        "unique_id": "ddpage_0I5ObjQ8",
                        "level": 1,
                        "is_test": false
                    }
                ]
            }""");
        
        // gd_special_price
        defaultConfs.put("business_gd_special_price", """
            {
                "name": "gd_special_price",
                "enable": true,
                "max_concurrent": 0,
                "sources": [
                    {
                        "unique_id": "ddpage_0I5ObjQ8",
                        "level": 0,
                        "is_test": false
                    },
                    {
                        "unique_id": "ddpage_0OzGhcaZ",
                        "level": 0,
                        "is_test": true
                    }
                ]
            }""");
        
        // xl_bubble
        defaultConfs.put("business_xl_bubble", """
            {
                "name": "xl_bubble",
                "enable": true,
                "max_concurrent": 0,
                "sources": [
                    {
                        "unique_id": "ddpage_0OosRo48",
                        "level": 1,
                        "is_test": false
                    }
                ]
            }""");
        
        // coupon_sp
        defaultConfs.put("business_coupon_sp", """
            {
                "name": "coupon_sp",
                "enable": true,
                "max_concurrent": 0,
                "sources": [
                    {
                        "unique_id": "ddpage_0Oo5sN8t",
                        "level": 1,
                        "is_test": false
                    }
                ]
            }""");
        
        // price_rule_xl
        defaultConfs.put("business_price_rule_xl", """
            {
                "name": "price_rule_xl",
                "enable": true,
                "max_concurrent": 0,
                "sources": [
                    {
                        "unique_id": "ddpage_0O8YPI7s",
                        "level": 1,
                        "is_test": false
                    }
                ]
            }""");
        
        return defaultConfs;
    }
    
    /**
     * 业务配置
     */
    @Data
    public static class BusinessConf {
        /**
         * 业务名称
         */
        private String name;
        
        /**
         * 是否启用
         */
        private boolean enable;
        
        /**
         * 访问大模型的最大并发，0 代表全部
         */
        private int maxConcurrent;
        
        /**
         * 源配置列表
         */
        private List<BusinessSourceConf> sources;
        
        /**
         * 根据sourceUniqueId获取源配置
         */
        public BusinessSourceConf getSourceConf(String sourceUniqueId) {
            if (sources == null) {
                return null;
            }
            for (BusinessSourceConf sourceConf : sources) {
                if (sourceUniqueId.equals(sourceConf.getUniqueId())) {
                    return sourceConf;
                }
            }
            return null;
        }
    }
    
    /**
     * 业务源配置
     */
    @Data
    public static class BusinessSourceConf {
        /**
         * 唯一ID
         */
        private String uniqueId;
        
        /**
         * 是否测试环境
         */
        private boolean isTest;
        
        /**
         * 重要等级 P0, P1, P2
         */
        private int level;
    }
}
