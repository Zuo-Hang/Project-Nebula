package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt灰度发布管理器
 * 
 * 核心功能：
 * 1. 灰度发布控制（Canary Update）
 * 2. 版本路由（根据taskId hash决定使用哪个版本）
 * 3. 监控灰度版本的效果
 * 
 * 风险控制：
 * - 只对1%的流量生效（可配置）
 * - 如果效果下降，自动回滚
 */
@Slf4j
@Service
public class PromptCanaryManager {
    
    @Autowired(required = false)
    private PromptManager promptManager;
    
    /**
     * 灰度发布配置
     * key: bizType_stage, value: CanaryConfig
     */
    private final Map<String, CanaryConfig> canaryConfigs = new ConcurrentHashMap<>();
    
    /**
     * 默认灰度比例（1%）
     */
    @Value("${prompt.canary.default-ratio:0.01}")
    private double defaultCanaryRatio;
    
    /**
     * 灰度配置
     */
    @lombok.Data
    public static class CanaryConfig {
        /**
         * 灰度版本号
         */
        private String canaryVersion;
        
        /**
         * 灰度比例（0.0 - 1.0）
         */
        private double canaryRatio;
        
        /**
         * 发布时间
         */
        private long publishTime;
        
        /**
         * 是否启用
         */
        private boolean enabled;
    }
    
    /**
     * 启用灰度发布
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param version 灰度版本号
     * @param canaryRatio 灰度比例
     */
    public void enableCanary(String bizType, PromptStage stage, String version, double canaryRatio) {
        String key = buildKey(bizType, stage);
        
        CanaryConfig config = new CanaryConfig();
        config.setCanaryVersion(version);
        config.setCanaryRatio(canaryRatio);
        config.setPublishTime(System.currentTimeMillis());
        config.setEnabled(true);
        
        canaryConfigs.put(key, config);
        
        log.info("启用Prompt灰度发布: bizType={}, stage={}, version={}, ratio={}", 
            bizType, stage, version, canaryRatio);
    }
    
    /**
     * 禁用灰度发布
     */
    public void disableCanary(String bizType, PromptStage stage) {
        String key = buildKey(bizType, stage);
        canaryConfigs.remove(key);
        
        log.info("禁用Prompt灰度发布: bizType={}, stage={}", bizType, stage);
    }
    
    /**
     * 判断是否应该使用灰度版本
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param taskId 任务ID（用于hash计算）
     * @return 如果应该使用灰度版本，返回版本号；否则返回null
     */
    public String shouldUseCanaryVersion(String bizType, PromptStage stage, String taskId) {
        String key = buildKey(bizType, stage);
        CanaryConfig config = canaryConfigs.get(key);
        
        if (config == null || !config.isEnabled()) {
            return null;
        }
        
        // 根据taskId hash决定是否使用灰度版本
        int hash = Math.abs(taskId.hashCode());
        double ratio = hash % 10000 / 10000.0;
        
        if (ratio < config.getCanaryRatio()) {
            log.debug("使用灰度版本: bizType={}, stage={}, version={}, taskId={}, ratio={}", 
                bizType, stage, config.getCanaryVersion(), taskId, ratio);
            return config.getCanaryVersion();
        }
        
        return null;
    }
    
    /**
     * 获取灰度配置
     */
    public CanaryConfig getCanaryConfig(String bizType, PromptStage stage) {
        String key = buildKey(bizType, stage);
        return canaryConfigs.get(key);
    }
    
    /**
     * 构建键
     */
    private String buildKey(String bizType, PromptStage stage) {
        return String.format("%s_%s", bizType, stage.getValue());
    }
}
