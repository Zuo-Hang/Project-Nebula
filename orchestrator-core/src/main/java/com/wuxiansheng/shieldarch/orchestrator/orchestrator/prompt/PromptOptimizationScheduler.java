package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt自动优化调度器
 * 
 * 定时任务：
 * 1. 评估Prompt效果
 * 2. 识别失败案例
 * 3. 自动优化Prompt
 * 4. 监控效果并自动回滚
 * 
 * 这是"自我进化的AI引擎"的闭环实现
 */
@Slf4j
@Component
public class PromptOptimizationScheduler {
    
    @Autowired(required = false)
    private AutoPromptOptimizer autoPromptOptimizer;
    
    @Autowired(required = false)
    private PromptEvaluator promptEvaluator;
    
    @Autowired(required = false)
    private PromptManager promptManager;
    
    /**
     * 是否启用自动优化调度
     */
    @Value("${prompt.auto-optimizer.scheduler.enabled:false}")
    private boolean enabled;
    
    /**
     * 评估间隔（小时）
     */
    @Value("${prompt.auto-optimizer.scheduler.interval-hours:24}")
    private int intervalHours;
    
    /**
     * 需要优化的业务类型列表
     */
    @Value("${prompt.auto-optimizer.biz-types:GAODE,XIAOLA}")
    private List<String> bizTypes;
    
    /**
     * 需要优化的Prompt阶段
     */
    private static final PromptStage[] STAGES_TO_OPTIMIZE = {
        PromptStage.EXTRACTION,
        PromptStage.REFLECT
    };
    
    /**
     * 版本监控记录（用于回滚判断）
     * key: bizType_stage_version, value: 发布时间
     */
    private final Map<String, Long> versionPublishTime = new HashMap<>();
    
    /**
     * 定时评估和优化Prompt
     * 
     * 每天执行一次（可配置）
     */
    @Scheduled(cron = "${prompt.auto-optimizer.scheduler.cron:0 0 2 * * ?}")
    public void scheduleOptimization() {
        if (!enabled) {
            log.debug("Prompt自动优化调度未启用，跳过执行");
            return;
        }
        
        if (autoPromptOptimizer == null || promptEvaluator == null) {
            log.warn("自动优化器或评估器未配置，跳过执行");
            return;
        }
        
        log.info("开始执行Prompt自动优化调度任务");
        
        try {
            // 1. 遍历所有业务类型和阶段
            for (String bizType : bizTypes) {
                for (PromptStage stage : STAGES_TO_OPTIMIZE) {
                    try {
                        optimizePromptForBiz(bizType, stage);
                    } catch (Exception e) {
                        log.error("优化Prompt失败: bizType={}, stage={}, error={}", 
                            bizType, stage, e.getMessage(), e);
                    }
                }
            }
            
            // 2. 检查已发布的版本，判断是否需要回滚
            checkAndRollbackVersions();
            
            log.info("Prompt自动优化调度任务执行完成");
            
        } catch (Exception e) {
            log.error("Prompt自动优化调度任务执行失败", e);
        }
    }
    
    /**
     * 优化指定业务的Prompt
     */
    private void optimizePromptForBiz(String bizType, PromptStage stage) {
        log.info("开始优化Prompt: bizType={}, stage={}", bizType, stage);
        
        // 1. 评估当前Prompt效果
        AutoPromptOptimizer.EvaluationResult evaluation = 
            promptEvaluator.evaluate(bizType, stage);
        
        // 2. 判断是否需要优化（成功率低于阈值）
        double successRateThreshold = 0.90; // 90%
        if (evaluation.getSuccessRate() >= successRateThreshold) {
            log.info("Prompt效果良好，无需优化: bizType={}, stage={}, successRate={}", 
                bizType, stage, evaluation.getSuccessRate());
            return;
        }
        
        // 3. 识别失败案例
        List<AutoPromptOptimizer.OptimizationRequest.FailureCase> failureCases = 
            promptEvaluator.identifyFailureCases(bizType, stage, 50);
        
        if (failureCases.isEmpty()) {
            log.warn("未找到失败案例，无法优化: bizType={}, stage={}", bizType, stage);
            return;
        }
        
        // 4. 获取当前Prompt模板内容
        String currentPrompt = promptManager.getCurrentPromptTemplate(bizType, stage);
        if (currentPrompt == null || currentPrompt.isEmpty()) {
            log.warn("无法获取当前Prompt模板: bizType={}, stage={}", bizType, stage);
            return;
        }
        
        // 5. 构建优化请求
        AutoPromptOptimizer.OptimizationRequest request = 
            new AutoPromptOptimizer.OptimizationRequest();
        request.setCurrentPrompt(currentPrompt);
        request.setFailureCases(failureCases);
        request.setFailureCount(failureCases.size());
        
        // 6. 执行自动优化
        AutoPromptOptimizer.OptimizationResult result = 
            autoPromptOptimizer.optimizePrompt(bizType, stage, request);
        
        if (result.isSuccess()) {
            log.info("Prompt自动优化成功: bizType={}, stage={}, version={}", 
                bizType, stage, result.getVersion());
            
            // 记录版本发布时间
            String versionKey = buildVersionKey(bizType, stage, result.getVersion());
            versionPublishTime.put(versionKey, System.currentTimeMillis());
        } else {
            log.warn("Prompt自动优化失败: bizType={}, stage={}, error={}", 
                bizType, stage, result.getErrorMessage());
        }
    }
    
    /**
     * 检查并回滚效果不佳的版本
     */
    private void checkAndRollbackVersions() {
        log.info("开始检查已发布版本的效果");
        
        for (Map.Entry<String, Long> entry : versionPublishTime.entrySet()) {
            String versionKey = entry.getKey();
            long publishTime = entry.getValue();
            
            // 解析版本信息
            String[] parts = versionKey.split("_");
            if (parts.length < 3) {
                continue;
            }
            
            String bizType = parts[0];
            String stageValue = parts[1];
            String version = parts[2];
            
            PromptStage stage = PromptStage.valueOf(stageValue);
            
            // 检查是否需要回滚
            if (autoPromptOptimizer.shouldRollback(bizType, stage, version)) {
                log.warn("检测到版本效果下降，触发自动回滚: bizType={}, stage={}, version={}", 
                    bizType, stage, version);
                
                boolean rolledBack = autoPromptOptimizer.autoRollback(bizType, stage, version);
                
                if (rolledBack) {
                    // 从监控记录中移除
                    versionPublishTime.remove(versionKey);
                    log.info("版本已回滚: bizType={}, stage={}, version={}", bizType, stage, version);
                }
            }
        }
    }
    
    /**
     * 构建版本键
     */
    private String buildVersionKey(String bizType, PromptStage stage, String version) {
        return String.format("%s_%s_%s", bizType, stage.getValue(), version);
    }
}
