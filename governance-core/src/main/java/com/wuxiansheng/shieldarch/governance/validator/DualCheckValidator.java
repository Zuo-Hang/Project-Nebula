package com.wuxiansheng.shieldarch.governance.validator;

import com.wuxiansheng.shieldarch.governance.registry.BusinessStrategyRegistry;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.LLMServiceClient;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 双路校验器
 * 
 * 实现规则校验和语义校验两种校验方式：
 * 1. 规则校验：调用BusinessStrategyRegistry中注册的规则（如价格波动检查）
 * 2. 语义校验：构造Prompt让轻量级LLM（如Qwen2-Chat）检查字段间逻辑矛盾
 */
@Slf4j
@Component
public class DualCheckValidator {
    
    @Autowired(required = false)
    private BusinessStrategyRegistry businessStrategyRegistry;
    
    @Autowired(required = false)
    private LLMServiceClient llmServiceClient;
    
    /**
     * 是否启用语义校验
     */
    @Value("${governance.validator.semantic-check.enabled:true}")
    private boolean semanticCheckEnabled;
    
    /**
     * 语义校验使用的LLM模型（轻量级模型，如Qwen2-Chat）
     */
    @Value("${governance.validator.semantic-check.model:qwen2-chat}")
    private String semanticCheckModel;
    
    /**
     * 校验结果
     */
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();
        private Map<String, Object> details = new HashMap<>();
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
        
        public void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }
        
        public Map<String, Object> getDetails() {
            return details;
        }
        
        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
    
    /**
     * 执行双路校验
     * 
     * @param context 任务上下文
     * @param content LLM推理结果内容（JSON格式字符串）
     * @return 校验结果
     */
    public ValidationResult validate(TaskContext context, String content) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        
        log.info("开始双路校验: taskId={}", context.getTaskId());
        
        // 1. 规则校验
        List<BusinessStrategyRegistry.ValidationResult> ruleResults = performRuleValidation(context, content);
        for (BusinessStrategyRegistry.ValidationResult ruleResult : ruleResults) {
            if (!ruleResult.isValid()) {
                result.addError(String.format("规则校验失败 [%s]: %s", 
                    ruleResult.getRuleName(), ruleResult.getErrorMessage()));
            }
        }
        
        // 2. 语义校验
        if (semanticCheckEnabled && llmServiceClient != null) {
            String semanticError = performSemanticValidation(context, content);
            if (semanticError != null && !semanticError.isEmpty()) {
                result.addError("语义校验失败: " + semanticError);
            }
        } else {
            log.debug("语义校验已禁用或LLM服务未配置，跳过");
        }
        
        // 记录校验详情
        result.getDetails().put("ruleValidationCount", ruleResults.size());
        result.getDetails().put("semanticCheckEnabled", semanticCheckEnabled);
        
        log.info("双路校验完成: taskId={}, valid={}, errorCount={}", 
            context.getTaskId(), result.isValid(), result.getErrors().size());
        
        return result;
    }
    
    /**
     * 执行规则校验
     */
    private List<BusinessStrategyRegistry.ValidationResult> performRuleValidation(
            TaskContext context, String content) {
        
        if (businessStrategyRegistry == null) {
            log.warn("BusinessStrategyRegistry未配置，跳过规则校验");
            return new ArrayList<>();
        }
        
        return businessStrategyRegistry.validateAll(context, content);
    }
    
    /**
     * 执行语义校验
     * 
     * 构造Prompt让轻量级LLM检查字段间逻辑矛盾
     */
    private String performSemanticValidation(TaskContext context, String content) {
        try {
            // 构造语义校验Prompt
            String prompt = buildSemanticValidationPrompt(content);
            
            // 调用轻量级LLM进行校验
            String validationResult = llmServiceClient.infer(prompt, null, content);
            
            // 解析校验结果
            if (validationResult == null || validationResult.isEmpty()) {
                return null;
            }
            
            // 检查校验结果中是否包含错误
            if (validationResult.toLowerCase().contains("error") || 
                validationResult.toLowerCase().contains("invalid") ||
                validationResult.toLowerCase().contains("矛盾") ||
                validationResult.toLowerCase().contains("不一致")) {
                return validationResult;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("语义校验执行失败: taskId={}, error={}", context.getTaskId(), e.getMessage(), e);
            return "语义校验执行异常: " + e.getMessage();
        }
    }
    
    /**
     * 构建语义校验Prompt
     */
    private String buildSemanticValidationPrompt(String content) {
        return String.format(
            "请检查以下JSON数据中是否存在逻辑矛盾或不一致的地方。\n" +
            "重点关注：\n" +
            "1. 数值字段之间的逻辑关系（如价格、数量、总价等）\n" +
            "2. 时间字段的合理性（如开始时间应早于结束时间）\n" +
            "3. 枚举字段的值是否合法\n" +
            "4. 必填字段是否缺失\n" +
            "5. 字段之间的依赖关系是否正确\n\n" +
            "如果发现任何问题，请详细说明。如果没有问题，请回复\"验证通过\"。\n\n" +
            "数据内容：\n%s", content);
    }
}

