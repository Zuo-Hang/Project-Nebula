package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 输入验证器
 * 
 * 功能：
 * 1. 长度检查
 * 2. 格式验证
 * 3. Prompt注入检测
 * 4. 内容过滤
 * 
 * 这是Prompt安全的第一道防线
 */
@Slf4j
@Component
public class InputValidator {
    
    @Autowired(required = false)
    private PromptInjectionDetector promptInjectionDetector;
    
    @Autowired(required = false)
    private ContentFilter contentFilter;
    
    /**
     * 最大输入长度
     */
    @Value("${prompt.security.max-input-length:10000}")
    private int maxInputLength;
    
    /**
     * 最小输入长度
     */
    @Value("${prompt.security.min-input-length:1}")
    private int minInputLength;
    
    /**
     * 是否启用严格模式（拒绝所有可疑输入）
     */
    @Value("${prompt.security.strict-mode:true}")
    private boolean strictMode;
    
    /**
     * 验证输入
     * 
     * @param input 用户输入
     * @return 验证结果
     */
    public ValidationResult validate(String input) {
        ValidationResult result = new ValidationResult();
        result.setOriginalInput(input);
        
        if (input == null) {
            result.setValid(false);
            result.setErrorMessage("输入不能为空");
            return result;
        }
        
        // 1. 长度检查
        if (input.length() > maxInputLength) {
            result.setValid(false);
            result.setErrorMessage(String.format("输入长度超过限制: %d > %d", 
                input.length(), maxInputLength));
            log.warn("输入验证失败: 长度超限, length={}", input.length());
            return result;
        }
        
        if (input.length() < minInputLength) {
            result.setValid(false);
            result.setErrorMessage(String.format("输入长度不足: %d < %d", 
                input.length(), minInputLength));
            return result;
        }
        
        // 2. Prompt注入检测
        if (promptInjectionDetector != null) {
            PromptInjectionDetector.InjectionDetectionResult injectionResult = 
                promptInjectionDetector.detectInjectionWithDetails(input);
            
            if (injectionResult.isDetected()) {
                result.setValid(false);
                result.setErrorMessage("检测到Prompt注入攻击: " + injectionResult.getMatchedPattern());
                result.setSecurityIssue("PROMPT_INJECTION");
                log.warn("输入验证失败: Prompt注入攻击, pattern={}", 
                    injectionResult.getMatchedPattern());
                return result;
            }
        }
        
        // 3. 内容过滤
        String filteredInput = input;
        if (contentFilter != null) {
            ContentFilter.FilterResult filterResult = contentFilter.filterWithDetails(input);
            
            if (strictMode && !filterResult.isSafe()) {
                // 严格模式：如果包含PII或敏感信息，拒绝输入
                result.setValid(false);
                result.setErrorMessage("输入包含敏感信息，已拒绝");
                result.setSecurityIssue("SENSITIVE_CONTENT");
                log.warn("输入验证失败: 包含敏感信息, PII={}, Sensitive={}", 
                    filterResult.isContainsPII(), filterResult.isContainsSensitiveInfo());
                return result;
            } else {
                // 非严格模式：过滤后继续
                filteredInput = filterResult.getFilteredContent();
                result.setFilteredInput(filteredInput);
                result.setContainsPII(filterResult.isContainsPII());
                result.setContainsSensitiveInfo(filterResult.isContainsSensitiveInfo());
            }
        }
        
        // 4. 格式验证（可选）
        if (!isValidFormat(filteredInput)) {
            result.setValid(false);
            result.setErrorMessage("输入格式不正确");
            return result;
        }
        
        // 验证通过
        result.setValid(true);
        result.setValidatedInput(filteredInput);
        log.debug("输入验证通过: length={}", filteredInput.length());
        
        return result;
    }
    
    /**
     * 验证输入格式
     * 
     * 可以扩展为更复杂的格式验证
     */
    private boolean isValidFormat(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        // 检查是否包含过多的特殊字符（可能是编码攻击）
        long specialCharCount = input.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();
        
        double specialCharRatio = (double) specialCharCount / input.length();
        
        // 如果特殊字符占比超过50%，可能是异常输入
        if (specialCharRatio > 0.5) {
            log.warn("输入格式异常: 特殊字符占比过高, ratio={}", specialCharRatio);
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证结果
     */
    @lombok.Data
    public static class ValidationResult {
        /**
         * 原始输入
         */
        private String originalInput;
        
        /**
         * 是否有效
         */
        private boolean valid;
        
        /**
         * 验证后的输入（已过滤）
         */
        private String validatedInput;
        
        /**
         * 过滤后的输入（如果进行了过滤）
         */
        private String filteredInput;
        
        /**
         * 错误信息
         */
        private String errorMessage;
        
        /**
         * 安全问题类型
         */
        private String securityIssue;
        
        /**
         * 是否包含PII
         */
        private boolean containsPII;
        
        /**
         * 是否包含敏感信息
         */
        private boolean containsSensitiveInfo;
    }
}
