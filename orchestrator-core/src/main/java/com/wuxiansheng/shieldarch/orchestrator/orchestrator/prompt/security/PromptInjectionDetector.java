package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt注入检测器
 * 
 * 检测常见的Prompt注入攻击模式，防止恶意用户通过特殊输入
 * 绕过系统提示词，控制AI行为。
 * 
 * 参考：OWASP LLM Top 10 - LLM01: Prompt Injection
 */
@Slf4j
@Component
public class PromptInjectionDetector {
    
    /**
     * 常见的Prompt注入模式
     * 
     * 这些模式试图让AI忽略原始指令或执行恶意操作
     */
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        // 忽略指令类
        Pattern.compile("ignore.*previous.*instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget.*everything", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard.*above", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ignore.*above.*instructions?", Pattern.CASE_INSENSITIVE),
        
        // 角色扮演类
        Pattern.compile("you.*are.*now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pretend.*you.*are", Pattern.CASE_INSENSITIVE),
        Pattern.compile("act.*as.*if", Pattern.CASE_INSENSITIVE),
        Pattern.compile("roleplay.*as", Pattern.CASE_INSENSITIVE),
        
        // 系统指令类
        Pattern.compile("system.*prompt", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system.*message", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system.*instruction", Pattern.CASE_INSENSITIVE),
        
        // 输出格式控制类
        Pattern.compile("output.*only", Pattern.CASE_INSENSITIVE),
        Pattern.compile("print.*only", Pattern.CASE_INSENSITIVE),
        Pattern.compile("respond.*only", Pattern.CASE_INSENSITIVE),
        
        // 特殊标记类
        Pattern.compile("\\[INST\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[/INST\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\|im_start\\|>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\|im_end\\|>", Pattern.CASE_INSENSITIVE),
        
        // 编码绕过类
        Pattern.compile("base64.*decode", Pattern.CASE_INSENSITIVE),
        Pattern.compile("hex.*decode", Pattern.CASE_INSENSITIVE),
        Pattern.compile("rot13.*decode", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * 检测是否包含Prompt注入攻击
     * 
     * @param userInput 用户输入
     * @return 如果检测到注入攻击，返回true
     */
    public boolean detectInjection(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return false;
        }
        
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                log.warn("检测到Prompt注入攻击: pattern={}, input={}", 
                    pattern.pattern(), sanitizeForLog(userInput));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检测并返回详细信息
     * 
     * @param userInput 用户输入
     * @return 检测结果，包含是否检测到注入和匹配的模式
     */
    public InjectionDetectionResult detectInjectionWithDetails(String userInput) {
        InjectionDetectionResult result = new InjectionDetectionResult();
        result.setInput(userInput);
        
        if (userInput == null || userInput.isEmpty()) {
            result.setDetected(false);
            return result;
        }
        
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                result.setDetected(true);
                result.setMatchedPattern(pattern.pattern());
                result.setSeverity(InjectionSeverity.HIGH);
                log.warn("检测到Prompt注入攻击: pattern={}, input={}", 
                    pattern.pattern(), sanitizeForLog(userInput));
                return result;
            }
        }
        
        result.setDetected(false);
        return result;
    }
    
    /**
     * 清理输入中的潜在注入内容
     * 
     * @param userInput 用户输入
     * @return 清理后的输入
     */
    public String sanitizeInput(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return userInput;
        }
        
        String sanitized = userInput;
        
        // 移除特殊标记
        sanitized = sanitized.replaceAll("\\[INST\\]", "");
        sanitized = sanitized.replaceAll("\\[/INST\\]", "");
        sanitized = sanitized.replaceAll("<\\|im_start\\|>", "");
        sanitized = sanitized.replaceAll("<\\|im_end\\|>", "");
        
        // 移除明显的注入指令
        sanitized = sanitized.replaceAll("(?i)ignore.*previous.*instructions?", "");
        sanitized = sanitized.replaceAll("(?i)forget.*everything", "");
        
        return sanitized.trim();
    }
    
    /**
     * 清理日志输出（避免敏感信息泄露）
     */
    private String sanitizeForLog(String input) {
        if (input == null) {
            return null;
        }
        
        // 限制日志长度
        if (input.length() > 200) {
            return input.substring(0, 200) + "...";
        }
        
        return input;
    }
    
    /**
     * 注入检测结果
     */
    @lombok.Data
    public static class InjectionDetectionResult {
        /**
         * 用户输入
         */
        private String input;
        
        /**
         * 是否检测到注入
         */
        private boolean detected;
        
        /**
         * 匹配的模式
         */
        private String matchedPattern;
        
        /**
         * 严重程度
         */
        private InjectionSeverity severity;
    }
    
    /**
     * 注入严重程度
     */
    public enum InjectionSeverity {
        LOW,    // 低风险
        MEDIUM, // 中风险
        HIGH    // 高风险
    }
}
