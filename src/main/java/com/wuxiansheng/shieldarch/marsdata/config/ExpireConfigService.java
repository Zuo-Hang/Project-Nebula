package com.wuxiansheng.shieldarch.marsdata.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 过期配置服务
 */
@Slf4j
@Service
public class ExpireConfigService {
    
    @Autowired
    private AppConfigService appConfigService;
    
    /**
     * 默认过期阈值（秒）：48小时
     */
    private static final long DEFAULT_EXPIRE_THRESHOLD = 86400L * 2;
    
    /**
     * 是否使用过期数据
     * 
     * @param businessName 业务名称
     * @return 是否使用过期数据
     */
    public boolean isUseExpireData(String businessName) {
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        
        if (params.isEmpty()) {
            log.error("获取配置失败: {}", AppConfigService.OCR_LLM_CONF);
            return false;
        }
        
        String useExpireData = params.get("use_expire_data");
        if (useExpireData == null || useExpireData.isEmpty()) {
            return false;
        }
        
        // 检查是否包含该业务名称
        return stringSplitContains(useExpireData, ",", businessName);
    }
    
    /**
     * 获取过期阈值（秒）
     * 
     * @param businessName 业务名称
     * @return 过期阈值（秒），默认48小时
     */
    public long getExpireDataThreshold(String businessName) {
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        
        if (params.isEmpty()) {
            log.error("获取配置失败: {}, 使用默认值: {}", 
                AppConfigService.OCR_LLM_CONF, DEFAULT_EXPIRE_THRESHOLD);
            return DEFAULT_EXPIRE_THRESHOLD;
        }
        
        String confName = "expire_threshold_" + businessName;
        String thresholdStr = params.get(confName);
        
        if (thresholdStr == null || thresholdStr.isEmpty()) {
            return DEFAULT_EXPIRE_THRESHOLD;
        }
        
        try {
            long threshold = Long.parseLong(thresholdStr);
            return threshold;
        } catch (NumberFormatException e) {
            log.warn("解析过期阈值失败: confName={}, value={}, 使用默认值: {}", 
                confName, thresholdStr, DEFAULT_EXPIRE_THRESHOLD);
            return DEFAULT_EXPIRE_THRESHOLD;
        }
    }
    
    /**
     * 逗号分隔的字符串是否包含目标字符串
     * 
     * @param str 逗号分隔的字符串
     * @param sep 分隔符
     * @param target 目标字符串
     * @return 是否包含
     */
    private boolean stringSplitContains(String str, String sep, String target) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        List<String> parts = Arrays.asList(str.split(sep));
        for (String part : parts) {
            String trimmed = part.trim();
            if ("all".equals(trimmed) || target.equals(trimmed)) {
                return true;
            }
        }
        
        return false;
    }
}
