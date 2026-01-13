package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.template;

import com.github.jknack.handlebars.Handlebars;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt模板变量验证器
 * 
 * 功能：
 * 1. 提取模板中的变量
 * 2. 验证必需变量是否提供
 * 3. 检查未使用的变量
 * 
 * 参考：LangChain PromptTemplate变量验证
 */
@Slf4j
@Component
public class PromptTemplateValidator {
    
    /**
     * Handlebars变量模式：{{variable}} 或 {{#if variable}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "\\{\\{([^}]+)\\}\\}"
    );
    
    /**
     * 验证模板变量
     * 
     * @param template 模板内容
     * @param providedVariables 提供的变量
     * @return 验证结果
     */
    public ValidationResult validate(String template, Set<String> providedVariables) {
        ValidationResult result = new ValidationResult();
        result.setTemplate(template);
        
        if (template == null || template.isEmpty()) {
            result.setValid(false);
            result.setErrorMessage("模板为空");
            return result;
        }
        
        // 1. 提取模板中的所有变量
        Set<String> requiredVariables = extractVariables(template);
        result.setRequiredVariables(requiredVariables);
        
        // 2. 检查必需变量是否提供
        Set<String> missingVariables = new HashSet<>(requiredVariables);
        if (providedVariables != null) {
            missingVariables.removeAll(providedVariables);
        }
        
        result.setMissingVariables(missingVariables);
        result.setValid(missingVariables.isEmpty());
        
        if (!missingVariables.isEmpty()) {
            result.setErrorMessage("缺少必需变量: " + String.join(", ", missingVariables));
            log.warn("模板变量验证失败: missingVariables={}", missingVariables);
        }
        
        // 3. 检查未使用的变量
        if (providedVariables != null) {
            Set<String> unusedVariables = new HashSet<>(providedVariables);
            unusedVariables.removeAll(requiredVariables);
            result.setUnusedVariables(unusedVariables);
        }
        
        return result;
    }
    
    /**
     * 提取模板中的变量
     */
    private Set<String> extractVariables(String template) {
        Set<String> variables = new HashSet<>();
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String variableExpr = matcher.group(1).trim();
            
            // 处理Handlebars表达式
            // 例如：{{variable}}, {{#if variable}}, {{#each items}}
            String variable = extractVariableName(variableExpr);
            if (variable != null && !variable.isEmpty()) {
                variables.add(variable);
            }
        }
        
        return variables;
    }
    
    /**
     * 从Handlebars表达式中提取变量名
     */
    private String extractVariableName(String expression) {
        // 移除Handlebars指令（如 #if, #each, /if, /each等）
        expression = expression.replaceAll("^#?/?if\\s+", "");
        expression = expression.replaceAll("^#?/?each\\s+", "");
        expression = expression.replaceAll("^#?/?with\\s+", "");
        expression = expression.replaceAll("^@", "");  // @index, @key等
        expression = expression.replaceAll("^this\\.", "");  // this.field
        
        // 提取变量名（第一个单词）
        String[] parts = expression.trim().split("\\s+");
        if (parts.length > 0) {
            return parts[0];
        }
        
        return expression.trim();
    }
    
    /**
     * 验证结果
     */
    @lombok.Data
    public static class ValidationResult {
        /**
         * 模板内容
         */
        private String template;
        
        /**
         * 是否有效
         */
        private boolean valid;
        
        /**
         * 错误信息
         */
        private String errorMessage;
        
        /**
         * 必需变量
         */
        private Set<String> requiredVariables = new HashSet<>();
        
        /**
         * 缺少的变量
         */
        private Set<String> missingVariables = new HashSet<>();
        
        /**
         * 未使用的变量
         */
        private Set<String> unusedVariables = new HashSet<>();
    }
}
