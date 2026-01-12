package com.wuxiansheng.shieldarch.marsdata.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.AppConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.RedisWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * LLM缓存服务
 */
@Slf4j
@Service
public class LLMCacheService {
    
    @Autowired(required = false)
    private RedisWrapper redisWrapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AppConfigService appConfigService;
    
    /**
     * 最小缓存TTL（秒）：1分钟
     */
    private static final long MIN_CACHE_TTL_SECONDS = 60;
    
    /**
     * 最大缓存TTL（秒）：30天
     */
    private static final long MAX_CACHE_TTL_SECONDS = 86400L * 30;
    
    /**
     * 默认缓存TTL（秒）：7天
     */
    private static final long DEFAULT_CACHE_TTL_SECONDS = 86400L * 7;
    
    /**
     * LLM缓存结果
     */
    @Data
    public static class LLMCacheResult {
        /**
         * LLM识别结果
         */
        private String content;
        
        /**
         * 缓存时间戳（秒）
         */
        private long timestamp;
        
        /**
         * 业务名称，用于区分不同链路
         */
        private String businessName;
    }
    
    /**
     * 生成缓存key
     * 
     * @param imageURL 图片URL
     * @param businessName 业务名称
     * @param prompt 提示词
     * @return 缓存key
     */
    public String generateCacheKey(String imageURL, String businessName, String prompt) {
        String imageHash = sha256Hash(imageURL);
        String promptHash = sha256Hash(prompt);
        return String.format("llm_cache:%s:%s:%s", businessName, promptHash, imageHash);
    }
    
    /**
     * SHA256哈希
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA256算法不可用", e);
            // 降级：使用简单的hashCode
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * 获取LLM缓存
     * 
     * @param imageURL 图片URL
     * @param businessName 业务名称
     * @param prompt 提示词
     * @return 缓存结果，如果不存在则返回null
     */
    public LLMCacheResult getLLMCache(String imageURL, String businessName, String prompt) {
        if (redisWrapper == null || !redisWrapper.isRedisEnabled()) {
            log.debug("Redis未启用，无法获取缓存");
            return null;
        }
        
        try {
            String key = generateCacheKey(imageURL, businessName, prompt);
            String jsonData = redisWrapper.get(key);
            
            LLMCacheResult cacheResult = objectMapper.readValue(jsonData, LLMCacheResult.class);
            log.info("LLM缓存命中: key={}, content={}, business={}, timestamp={}", 
                key, cacheResult.getContent(), cacheResult.getBusinessName(), cacheResult.getTimestamp());
            
            return cacheResult;
            
        } catch (RedisWrapper.KeyNotFoundException e) {
            // 键不存在，返回null表示缓存未命中
            return null;
        } catch (Exception e) {
            log.error("获取LLM缓存失败: imageURL={}, businessName={}, error={}", 
                imageURL, businessName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 设置LLM缓存
     * 
     * @param imageURL 图片URL
     * @param businessName 业务名称
     * @param prompt 提示词
     * @param content 缓存内容
     * @return 是否设置成功
     */
    public boolean setLLMCache(String imageURL, String businessName, String prompt, String content) {
        // 获取TTL（支持按business配置）
        long ttl = getLLMCacheTTL(businessName);
        return setLLMCacheWithTTL(imageURL, businessName, prompt, content, ttl);
    }
    
    /**
     * 设置LLM缓存（可指定TTL）
     * 
     * @param imageURL 图片URL
     * @param businessName 业务名称
     * @param prompt 提示词
     * @param content 缓存内容
     * @param ttl 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean setLLMCacheWithTTL(String imageURL, String businessName, String prompt, 
                                      String content, long ttl) {
        if (redisWrapper == null || !redisWrapper.isRedisEnabled()) {
            log.debug("Redis未启用，无法设置缓存");
            return false;
        }
        
        try {
            LLMCacheResult cacheResult = new LLMCacheResult();
            cacheResult.setContent(content);
            cacheResult.setTimestamp(System.currentTimeMillis() / 1000);
            cacheResult.setBusinessName(businessName);
            
            String jsonData = objectMapper.writeValueAsString(cacheResult);
            String key = generateCacheKey(imageURL, businessName, prompt);
            
            redisWrapper.setEx(key, jsonData, ttl);
            
            log.info("LLM缓存设置成功: key={}", key);
            return true;
            
        } catch (Exception e) {
            log.error("设置LLM缓存失败: imageURL={}, businessName={}, error={}", 
                imageURL, businessName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查缓存是否有效
     * 
     * @param cacheResult 缓存结果
     * @param businessName 业务名称
     * @return 是否有效
     */
    public boolean isLLMCacheValid(LLMCacheResult cacheResult, String businessName) {
        if (cacheResult == null) {
            return false;
        }
        
        long ttl = getLLMCacheTTL(businessName);
        return isLLMCacheValidWithTTL(cacheResult, ttl);
    }
    
    /**
     * 检查缓存是否有效（可指定TTL）
     * 
     * @param cacheResult 缓存结果
     * @param ttl TTL（秒）
     * @return 是否有效
     */
    public boolean isLLMCacheValidWithTTL(LLMCacheResult cacheResult, long ttl) {
        if (cacheResult == null) {
            return false;
        }
        
        long now = System.currentTimeMillis() / 1000;
        return (now - cacheResult.getTimestamp()) < ttl;
    }
    
    /**
     * 获取LLM缓存的TTL时间（秒），支持按business配置
     * 
     * @param businessName 业务名称
     * @return TTL（秒）
     */
    private long getLLMCacheTTL(String businessName) {
        long defaultVal = DEFAULT_CACHE_TTL_SECONDS;
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.error("获取配置失败: {}, 使用默认TTL: {}", 
                AppConfigService.OCR_LLM_CONF, defaultVal);
            return defaultVal;
        }
        
        String confName = "llm_cache_ttl_" + businessName;
        String ttlStr = params.get(confName);
        
        if (ttlStr == null || ttlStr.isEmpty()) {
            return defaultVal;
        }
        
        try {
            long ttl = Long.parseLong(ttlStr);
            
            // 限制TTL范围：最小1分钟，最大30天
            if (ttl < MIN_CACHE_TTL_SECONDS) {
                log.warn("llm_cache_ttl太小: {}, 使用默认值: {}", ttl, defaultVal);
                return defaultVal;
            }
            if (ttl > MAX_CACHE_TTL_SECONDS) {
                log.warn("llm_cache_ttl太大: {}, 使用最大值: {}", ttl, MAX_CACHE_TTL_SECONDS);
                return MAX_CACHE_TTL_SECONDS;
            }
            
            return ttl;
        } catch (NumberFormatException e) {
            log.warn("解析llm_cache_ttl失败: confName={}, value={}, 使用默认值: {}", 
                confName, ttlStr, defaultVal);
            return defaultVal;
        }
    }
}

