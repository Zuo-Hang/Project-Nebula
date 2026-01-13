package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.wuxiansheng.shieldarch.orchestrator.config.NacosConfigService;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptCanaryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动Prompt优化器
 * 
 * 核心功能：
 * 1. 根据评估结果自动优化Prompt
 * 2. 通过配置中心API自动更新Prompt
 * 3. 支持灰度发布和自动回滚
 * 
 * 这是"自我进化的AI引擎"的核心组件
 */
@Slf4j
@Service
public class AutoPromptOptimizer {
    
    @Autowired(required = false)
    private NacosConfigService nacosConfigService;
    
    
    @Autowired(required = false)
    private PromptManager promptManager;
    
    @Autowired(required = false)
    private PromptEvaluator promptEvaluator;
    
    @Autowired(required = false)
    private PromptCanaryManager promptCanaryManager;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    /**
     * 配置命名空间
     */
    @Value("${prompt.config.namespace:PROMPT_TEMPLATES}")
    private String configNamespace;
    
    /**
     * 配置组
     */
    @Value("${prompt.config.group:DEFAULT_GROUP}")
    private String configGroup;
    
    /**
     * 是否启用自动优化
     */
    @Value("${prompt.auto-optimizer.enabled:false}")
    private boolean enabled;
    
    /**
     * 灰度发布比例（0.01 = 1%）
     */
    @Value("${prompt.auto-optimizer.canary-ratio:0.01}")
    private double canaryRatio;
    
    /**
     * 自动回滚阈值（成功率下降超过此值则回滚）
     */
    @Value("${prompt.auto-optimizer.rollback-threshold:0.05}")
    private double rollbackThreshold;
    
    /**
     * 自动优化Prompt
     * 
     * 流程：
     * 1. 评估当前Prompt的效果
     * 2. 如果效果不佳，使用高阶模型生成优化后的Prompt
     * 3. 灰度发布新Prompt（1%流量）
     * 4. 监控效果，如果下降则自动回滚
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param optimizationRequest 优化请求（包含失败案例等）
     * @return 优化结果
     */
    public OptimizationResult optimizePrompt(
            String bizType, 
            PromptStage stage, 
            OptimizationRequest optimizationRequest) {
        
        if (!enabled) {
            log.debug("自动Prompt优化未启用，跳过优化: bizType={}, stage={}", bizType, stage);
            return OptimizationResult.disabled();
        }
        
        if (nacosConfigService == null) {
            log.warn("Nacos ConfigService未配置，无法自动更新Prompt");
            return OptimizationResult.failed("Nacos ConfigService未配置");
        }
        
        log.info("开始自动优化Prompt: bizType={}, stage={}, failureCount={}", 
            bizType, stage, optimizationRequest.getFailureCount());
        
        try {
            // 1. 评估当前Prompt效果
            EvaluationResult currentEvaluation = evaluateCurrentPrompt(bizType, stage);
            
            // 2. 生成优化后的Prompt（使用高阶模型）
            String optimizedPrompt = generateOptimizedPrompt(bizType, stage, optimizationRequest);
            
            if (optimizedPrompt == null || optimizedPrompt.isEmpty()) {
                log.warn("生成优化Prompt失败: bizType={}, stage={}", bizType, stage);
                return OptimizationResult.failed("生成优化Prompt失败");
            }
            
            // 3. 灰度发布新Prompt
            String version = generateVersion();
            boolean published = publishPromptWithCanary(bizType, stage, optimizedPrompt, version);
            
            if (!published) {
                log.warn("灰度发布Prompt失败: bizType={}, stage={}, version={}", bizType, stage, version);
                return OptimizationResult.failed("灰度发布失败");
            }
            
            // 4. 启用灰度发布（通过PromptCanaryManager）
            if (promptCanaryManager != null) {
                promptCanaryManager.enableCanary(bizType, stage, version, canaryRatio);
                log.info("启用灰度发布: bizType={}, stage={}, version={}, ratio={}", 
                    bizType, stage, version, canaryRatio);
            }
            
            // 5. 记录优化信息（用于后续监控和回滚）
            recordOptimization(bizType, stage, version, currentEvaluation, optimizedPrompt);
            
            log.info("Prompt自动优化完成: bizType={}, stage={}, version={}, canaryRatio={}", 
                bizType, stage, version, canaryRatio);
            
            return OptimizationResult.success(version, optimizedPrompt, canaryRatio);
            
        } catch (Exception e) {
            log.error("自动优化Prompt失败: bizType={}, stage={}, error={}", bizType, stage, e.getMessage(), e);
            
            // 上报失败指标
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("biz_type", bizType);
                tags.put("stage", stage.getValue());
                tags.put("status", "failed");
                metricsClient.incrementCounter("prompt_auto_optimize_total", tags);
            }
            
            return OptimizationResult.failed("优化过程异常: " + e.getMessage());
        }
    }
    
    /**
     * 评估当前Prompt效果
     */
    private EvaluationResult evaluateCurrentPrompt(String bizType, PromptStage stage) {
        if (promptEvaluator != null) {
            return promptEvaluator.evaluate(bizType, stage);
        }
        
        // 如果没有评估器，返回默认评估结果
        return EvaluationResult.defaultResult();
    }
    
    /**
     * 生成优化后的Prompt（使用高阶模型）
     * 
     * 这里使用LLM来优化Prompt，参考DSPy的思想
     */
    private String generateOptimizedPrompt(
            String bizType, 
            PromptStage stage, 
            OptimizationRequest request) {
        
        // 构建优化请求Prompt
        String optimizationPrompt = buildOptimizationPrompt(bizType, stage, request);
        
        // 调用高阶模型（如GPT-4o或Qwen-Max）生成优化后的Prompt
        // 注意：这里需要注入一个专门用于优化的LLM客户端
        // 暂时返回null，需要后续实现
        
        log.info("生成优化Prompt: bizType={}, stage={}, failureCount={}", 
            bizType, stage, request.getFailureCount());
        
        // TODO: 实现高阶模型调用
        // 1. 注入专门的优化LLM客户端（不同于业务推理的LLM）
        // 2. 调用优化LLM生成新Prompt
        // 3. 验证新Prompt的格式和有效性
        
        return null; // 占位，需要实现
    }
    
    /**
     * 构建优化请求Prompt
     */
    private String buildOptimizationPrompt(
            String bizType, 
            PromptStage stage, 
            OptimizationRequest request) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位Prompt优化专家。\n\n");
        prompt.append("当前业务类型：").append(bizType).append("\n");
        prompt.append("Prompt阶段：").append(stage.getValue()).append("\n\n");
        
        prompt.append("当前Prompt：\n").append(request.getCurrentPrompt()).append("\n\n");
        
        prompt.append("发现的错误案例：\n");
        for (int i = 0; i < request.getFailureCases().size(); i++) {
            OptimizationRequest.FailureCase failureCase = request.getFailureCases().get(i);
            prompt.append(String.format("%d. 输入：%s\n", i + 1, failureCase.getInput()));
            prompt.append(String.format("   错误输出：%s\n", failureCase.getOutput()));
            prompt.append(String.format("   错误原因：%s\n\n", failureCase.getErrorReason()));
        }
        
        prompt.append("请根据以上错误信息，生成一个优化后的Prompt。\n");
        prompt.append("要求：\n");
        prompt.append("1. 明确指出需要修正的问题\n");
        prompt.append("2. 提供更清晰的指导\n");
        prompt.append("3. 确保输出结果符合校验要求\n");
        prompt.append("4. 保持原始任务的核心目标\n");
        prompt.append("5. 只返回优化后的Prompt，不要包含其他内容\n");
        
        return prompt.toString();
    }
    
    /**
     * 灰度发布Prompt（Canary Update）
     * 
     * 策略：
     * 1. 创建新版本的配置项（带版本号）
     * 2. 在PromptManager中实现灰度逻辑（根据taskId hash决定使用哪个版本）
     * 3. 监控新版本的效果
     */
    private boolean publishPromptWithCanary(
            String bizType, 
            PromptStage stage, 
            String optimizedPrompt, 
            String version) throws NacosException {
        
        // 构建配置键（带版本号）
        String configKey = buildConfigKey(bizType, stage, version);
        
        // 发布到配置中心
        boolean success = nacosConfigService.publishConfig(
            configKey,
            configGroup,
            optimizedPrompt,
            ConfigType.TEXT.getType()
        );
        
        if (success) {
            log.info("Prompt灰度发布成功: bizType={}, stage={}, version={}, configKey={}", 
                bizType, stage, version, configKey);
            
            // 上报指标
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("biz_type", bizType);
                tags.put("stage", stage.getValue());
                tags.put("version", version);
                tags.put("status", "published");
                metricsClient.incrementCounter("prompt_canary_publish_total", tags);
            }
        } else {
            log.error("Prompt灰度发布失败: bizType={}, stage={}, version={}", bizType, stage, version);
        }
        
        return success;
    }
    
    /**
     * 自动回滚Prompt
     * 
     * 当检测到新版本效果下降时，自动回滚到上一个版本
     */
    public boolean autoRollback(String bizType, PromptStage stage, String version) {
        if (nacosConfigService == null) {
            log.warn("Nacos ConfigService未配置，无法回滚Prompt");
            return false;
        }
        
        log.warn("检测到Prompt效果下降，触发自动回滚: bizType={}, stage={}, version={}", 
            bizType, stage, version);
        
        try {
            // 1. 删除问题版本
            boolean removed = nacosConfigService.removeConfig(
                buildConfigKey(bizType, stage, version),
                configGroup
            );
            
            if (removed) {
                // 禁用灰度发布
                if (promptCanaryManager != null) {
                    promptCanaryManager.disableCanary(bizType, stage);
                }
                
                log.info("Prompt自动回滚成功: bizType={}, stage={}, version={}", bizType, stage, version);
                
                // 上报指标
                if (metricsClient != null) {
                    Map<String, String> tags = new HashMap<>();
                    tags.put("biz_type", bizType);
                    tags.put("stage", stage.getValue());
                    tags.put("version", version);
                    metricsClient.incrementCounter("prompt_auto_rollback_total", tags);
                }
                
                return true;
            } else {
                log.error("Prompt自动回滚失败: bizType={}, stage={}, version={}", bizType, stage, version);
                return false;
            }
            
        } catch (NacosException e) {
            log.error("Prompt自动回滚异常: bizType={}, stage={}, version={}, error={}", 
                bizType, stage, version, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查是否需要回滚
     * 
     * 通过监控指标判断新版本的效果
     */
    public boolean shouldRollback(String bizType, PromptStage stage, String version) {
        if (promptEvaluator == null) {
            return false;
        }
        
        // 评估新版本的效果
        EvaluationResult newVersionEvaluation = promptEvaluator.evaluateVersion(bizType, stage, version);
        
        // 评估基准版本（latest）的效果
        EvaluationResult baselineEvaluation = promptEvaluator.evaluate(bizType, stage);
        
        // 如果新版本的成功率下降超过阈值，需要回滚
        double successRateDrop = baselineEvaluation.getSuccessRate() - newVersionEvaluation.getSuccessRate();
        
        if (successRateDrop > rollbackThreshold) {
            log.warn("检测到Prompt效果下降，需要回滚: bizType={}, stage={}, version={}, drop={}", 
                bizType, stage, version, successRateDrop);
            return true;
        }
        
        return false;
    }
    
    /**
     * 记录优化信息（用于后续监控和回滚）
     */
    private void recordOptimization(
            String bizType, 
            PromptStage stage, 
            String version, 
            EvaluationResult currentEvaluation,
            String optimizedPrompt) {
        
        // 可以存储到数据库或Redis，用于后续分析
        log.info("记录Prompt优化信息: bizType={}, stage={}, version={}, currentSuccessRate={}", 
            bizType, stage, version, currentEvaluation.getSuccessRate());
        
        // 上报指标
        if (metricsClient != null) {
            Map<String, String> tags = new HashMap<>();
            tags.put("biz_type", bizType);
            tags.put("stage", stage.getValue());
            tags.put("version", version);
            metricsClient.recordGauge("prompt_optimization_success_rate", 
                (long)(currentEvaluation.getSuccessRate() * 100), tags);
        }
    }
    
    /**
     * 构建配置键
     */
    private String buildConfigKey(String bizType, PromptStage stage, String version) {
        return String.format("%s_%s_%s", bizType, stage.getValue(), version);
    }
    
    /**
     * 生成版本号
     */
    private String generateVersion() {
        // 使用时间戳 + 随机数生成版本号
        // 格式：v{timestamp}_{random}
        long timestamp = System.currentTimeMillis();
        int random = (int)(Math.random() * 1000);
        return String.format("v%d_%d", timestamp, random);
    }
    
    /**
     * 优化请求
     */
    @lombok.Data
    public static class OptimizationRequest {
        /**
         * 当前Prompt
         */
        private String currentPrompt;
        
        /**
         * 失败案例列表
         */
        private java.util.List<FailureCase> failureCases;
        
        /**
         * 失败总数
         */
        private int failureCount;
        
        /**
         * 失败案例
         */
        @lombok.Data
        public static class FailureCase {
            /**
             * 输入（OCR文本等）
             */
            private String input;
            
            /**
             * 错误输出
             */
            private String output;
            
            /**
             * 错误原因
             */
            private String errorReason;
        }
    }
    
    /**
     * 优化结果
     */
    @lombok.Data
    public static class OptimizationResult {
        private boolean success;
        private String version;
        private String optimizedPrompt;
        private double canaryRatio;
        private String errorMessage;
        
        public static OptimizationResult success(String version, String optimizedPrompt, double canaryRatio) {
            OptimizationResult result = new OptimizationResult();
            result.setSuccess(true);
            result.setVersion(version);
            result.setOptimizedPrompt(optimizedPrompt);
            result.setCanaryRatio(canaryRatio);
            return result;
        }
        
        public static OptimizationResult failed(String errorMessage) {
            OptimizationResult result = new OptimizationResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }
        
        public static OptimizationResult disabled() {
            OptimizationResult result = new OptimizationResult();
            result.setSuccess(false);
            result.setErrorMessage("自动优化未启用");
            return result;
        }
    }
    
    /**
     * 评估结果
     */
    @lombok.Data
    public static class EvaluationResult {
        /**
         * 成功率（0.0 - 1.0）
         */
        private double successRate;
        
        /**
         * 总样本数
         */
        private int totalSamples;
        
        /**
         * 成功样本数
         */
        private int successSamples;
        
        /**
         * 失败样本数
         */
        private int failureSamples;
        
        public static EvaluationResult defaultResult() {
            EvaluationResult result = new EvaluationResult();
            result.setSuccessRate(1.0);
            result.setTotalSamples(0);
            result.setSuccessSamples(0);
            result.setFailureSamples(0);
            return result;
        }
    }
}
