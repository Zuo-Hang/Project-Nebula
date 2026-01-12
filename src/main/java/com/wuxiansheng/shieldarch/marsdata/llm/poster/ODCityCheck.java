package com.wuxiansheng.shieldarch.marsdata.llm.poster;

import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OD城市检查工具类
 */
@Slf4j
@Component
public class ODCityCheck {
    
    public static final String CITY_UNKNOWN = "未知";
    public static final String CITY_RIGHT = "城市正确";
    public static final String CITY_ERROR = "城市错误";
    
    @Autowired(required = false)
    private LLMConfigHelper llmConfigHelper;
    
    /**
     * 检查OD城市
     * 
     * @param startPOI 起点POI
     * @param cityName 城市名称
     * @param businessName 业务名称
     * @param threshold 相似度阈值
     * @return 检查结果
     */
    public String checkODCity(String startPOI, String cityName, String businessName, double threshold) {
        if (startPOI == null || startPOI.isEmpty() || cityName == null || cityName.isEmpty()) {
            return CITY_UNKNOWN;
        }
        
        if (llmConfigHelper == null) {
            return CITY_UNKNOWN;
        }
        
        // 获取OD配置
        Map<String, String> odCityMap = llmConfigHelper.getODs(businessName);
        if (odCityMap.isEmpty()) {
            return CITY_UNKNOWN;
        }
        
        // 查找相似度超过阈值的OD
        java.util.List<String> similarOdCitys = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : odCityMap.entrySet()) {
            String od = entry.getKey();
            double similarity = calculateSimilarity(startPOI, od);
            if (similarity > threshold) {
                similarOdCitys.add(entry.getValue());
            }
        }
        
        if (similarOdCitys.isEmpty()) {
            return CITY_UNKNOWN;
        }
        
        // 检查是否有匹配的城市
        for (String similarCity : similarOdCitys) {
            if (similarCity.equals(cityName)) {
                return CITY_RIGHT;
            }
        }
        
        return CITY_ERROR;
    }
    
    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }
        if (str1.isEmpty() && str2.isEmpty()) {
            return 1.0;
        }
        
        // 计算最长公共子序列长度（简化版）
        int common = 0;
        int minLen = Math.min(str1.length(), str2.length());
        for (int i = 0; i < minLen; i++) {
            if (str1.charAt(i) == str2.charAt(i)) {
                common++;
            }
        }
        
        // 也检查包含关系
        if (str1.contains(str2) || str2.contains(str1)) {
            common = Math.max(common, minLen);
        }
        
        int totalLength = str1.length() + str2.length();
        return totalLength == 0 ? 1.0 : 2.0 * common / totalLength;
    }
}

