package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 部分填充Prompt构建器
 * 
 * 功能：
 * 1. 支持分步填充Prompt变量
 * 2. 支持部分渲染
 * 3. 支持变量合并
 * 
 * 参考：LangChain PartialPromptTemplate
 */
@Slf4j
@Component
public class PartialPromptBuilder {
    
    /**
     * 部分填充的Prompt
     */
    @lombok.Data
    public static class PartialPrompt {
        /**
         * 模板内容
         */
        private String template;
        
        /**
         * 已填充的变量
         */
        private Map<String, Object> filledVariables = new HashMap<>();
        
        /**
         * 未填充的变量
         */
        private Set<String> unfilledVariables;
        
        /**
         * 是否完全填充
         */
        public boolean isComplete() {
            return unfilledVariables == null || unfilledVariables.isEmpty();
        }
        
        /**
         * 填充变量
         */
        public void fillVariable(String variable, Object value) {
            filledVariables.put(variable, value);
            if (unfilledVariables != null) {
                unfilledVariables.remove(variable);
            }
        }
        
        /**
         * 批量填充变量
         */
        public void fillVariables(Map<String, Object> variables) {
            if (variables != null) {
                filledVariables.putAll(variables);
                if (unfilledVariables != null) {
                    unfilledVariables.removeAll(variables.keySet());
                }
            }
        }
    }
    
    /**
     * 创建部分填充的Prompt
     * 
     * @param template 模板内容
     * @param initialVariables 初始变量（可选）
     * @return 部分填充的Prompt
     */
    public PartialPrompt createPartialPrompt(String template, Map<String, Object> initialVariables) {
        PartialPrompt partialPrompt = new PartialPrompt();
        partialPrompt.setTemplate(template);
        
        // 提取未填充的变量
        PromptTemplateValidator validator = new PromptTemplateValidator();
        PromptTemplateValidator.ValidationResult validation = 
            validator.validate(template, initialVariables != null ? initialVariables.keySet() : null);
        
        partialPrompt.setUnfilledVariables(validation.getRequiredVariables());
        
        // 填充初始变量
        if (initialVariables != null) {
            partialPrompt.fillVariables(initialVariables);
        }
        
        log.debug("创建部分填充Prompt: templateLength={}, filledVariables={}, unfilledVariables={}", 
            template.length(), 
            partialPrompt.getFilledVariables().size(), 
            partialPrompt.getUnfilledVariables().size());
        
        return partialPrompt;
    }
    
    /**
     * 渲染部分填充的Prompt
     * 
     * @param partialPrompt 部分填充的Prompt
     * @return 渲染后的Prompt（未填充的变量保持原样）
     */
    public String renderPartial(PartialPrompt partialPrompt) {
        if (partialPrompt == null || partialPrompt.getTemplate() == null) {
            return "";
        }
        
        String template = partialPrompt.getTemplate();
        Map<String, Object> variables = partialPrompt.getFilledVariables();
        
        // 简单实现：替换已填充的变量
        // 实际应该使用Handlebars的部分渲染功能
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, 
                entry.getValue() != null ? entry.getValue().toString() : "");
        }
        
        log.debug("渲染部分填充Prompt: renderedLength={}, filledVariables={}", 
            rendered.length(), variables.size());
        
        return rendered;
    }
    
    /**
     * 合并两个部分填充的Prompt
     * 
     * @param prompt1 第一个Prompt
     * @param prompt2 第二个Prompt
     * @return 合并后的Prompt
     */
    public PartialPrompt merge(PartialPrompt prompt1, PartialPrompt prompt2) {
        if (prompt1 == null) {
            return prompt2;
        }
        if (prompt2 == null) {
            return prompt1;
        }
        
        PartialPrompt merged = new PartialPrompt();
        merged.setTemplate(prompt1.getTemplate() + "\n\n" + prompt2.getTemplate());
        
        // 合并变量
        Map<String, Object> mergedVariables = new HashMap<>(prompt1.getFilledVariables());
        mergedVariables.putAll(prompt2.getFilledVariables());
        merged.setFilledVariables(mergedVariables);
        
        // 合并未填充变量
        Set<String> mergedUnfilled = new java.util.HashSet<>();
        if (prompt1.getUnfilledVariables() != null) {
            mergedUnfilled.addAll(prompt1.getUnfilledVariables());
        }
        if (prompt2.getUnfilledVariables() != null) {
            mergedUnfilled.addAll(prompt2.getUnfilledVariables());
        }
        mergedUnfilled.removeAll(mergedVariables.keySet());
        merged.setUnfilledVariables(mergedUnfilled);
        
        log.debug("合并部分填充Prompt: mergedLength={}, filledVariables={}", 
            merged.getTemplate().length(), mergedVariables.size());
        
        return merged;
    }
}
