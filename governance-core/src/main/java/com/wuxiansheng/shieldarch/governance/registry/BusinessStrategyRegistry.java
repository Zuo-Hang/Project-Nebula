package com.wuxiansheng.shieldarch.governance.registry;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务规则注册表
 * 
 * 用于注册和管理各种业务校验规则
 */
@Slf4j
@Component
public class BusinessStrategyRegistry {
    
    /**
     * 规则注册表（规则名称 -> 规则实现）
     */
    private final Map<String, ValidationRule> rules = new ConcurrentHashMap<>();
    
    /**
     * 注册业务规则
     * 
     * @param ruleName 规则名称
     * @param rule 规则实现
     */
    public void registerRule(String ruleName, ValidationRule rule) {
        rules.put(ruleName, rule);
        log.info("业务规则已注册: {}", ruleName);
    }
    
    /**
     * 取消注册规则
     * 
     * @param ruleName 规则名称
     */
    public void unregisterRule(String ruleName) {
        rules.remove(ruleName);
        log.info("业务规则已取消注册: {}", ruleName);
    }
    
    /**
     * 获取规则
     * 
     * @param ruleName 规则名称
     * @return 规则实现，如果不存在返回null
     */
    public ValidationRule getRule(String ruleName) {
        return rules.get(ruleName);
    }
    
    /**
     * 获取所有规则名称
     * 
     * @return 规则名称列表
     */
    public List<String> getAllRuleNames() {
        return new ArrayList<>(rules.keySet());
    }
    
    /**
     * 执行所有注册的规则校验
     * 
     * @param context 任务上下文
     * @param content LLM推理结果内容
     * @return 校验结果列表
     */
    public List<ValidationResult> validateAll(TaskContext context, String content) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (Map.Entry<String, ValidationRule> entry : rules.entrySet()) {
            String ruleName = entry.getKey();
            ValidationRule rule = entry.getValue();
            
            try {
                ValidationResult result = rule.validate(context, content);
                result.setRuleName(ruleName);
                results.add(result);
                
                if (!result.isValid()) {
                    log.warn("规则校验失败: rule={}, error={}", ruleName, result.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("执行规则校验异常: rule={}, error={}", ruleName, e.getMessage(), e);
                ValidationResult errorResult = new ValidationResult();
                errorResult.setRuleName(ruleName);
                errorResult.setValid(false);
                errorResult.setErrorMessage("规则执行异常: " + e.getMessage());
                results.add(errorResult);
            }
        }
        
        return results;
    }
    
    /**
     * 校验规则接口
     */
    public interface ValidationRule {
        /**
         * 执行校验
         * 
         * @param context 任务上下文
         * @param content LLM推理结果内容
         * @return 校验结果
         */
        ValidationResult validate(TaskContext context, String content);
    }
    
    /**
     * 校验结果
     */
    public static class ValidationResult {
        private String ruleName;
        private boolean valid;
        private String errorMessage;
        private Map<String, Object> details;
        
        public String getRuleName() {
            return ruleName;
        }
        
        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public Map<String, Object> getDetails() {
            return details;
        }
        
        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
}

