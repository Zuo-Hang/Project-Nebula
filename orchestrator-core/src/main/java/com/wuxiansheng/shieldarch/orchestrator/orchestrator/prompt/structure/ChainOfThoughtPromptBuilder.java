package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.structure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Chain-of-Thought Prompt构建器
 * 
 * 功能：
 * 1. 构建显式推理过程的Prompt
 * 2. 引导AI逐步思考
 * 3. 提高推理的可解释性
 * 
 * 参考：Chain-of-Thought Prompting (Wei et al., 2022)
 */
@Slf4j
@Component
public class ChainOfThoughtPromptBuilder {
    
    /**
     * 构建Chain-of-Thought Prompt
     * 
     * @param question 问题
     * @param context 上下文信息
     * @param steps 推理步骤（可选，如果提供则使用，否则让AI自己生成）
     * @return CoT Prompt
     */
    public String buildCoTPrompt(String question, Map<String, Object> context, String... steps) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. 问题描述
        prompt.append("问题：").append(question).append("\n\n");
        
        // 2. 上下文信息（如果有）
        if (context != null && !context.isEmpty()) {
            prompt.append("上下文信息：\n");
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ")
                      .append(entry.getValue()).append("\n");
            }
            prompt.append("\n");
        }
        
        // 3. 推理步骤指导
        prompt.append("让我们一步步思考：\n");
        
        if (steps != null && steps.length > 0) {
            // 如果提供了步骤，使用提供的步骤
            for (int i = 0; i < steps.length; i++) {
                prompt.append(String.format("%d. %s\n", i + 1, steps[i]));
            }
        } else {
            // 否则，提供通用的推理步骤模板
            prompt.append("1. 首先，我需要理解问题的关键点\n");
            prompt.append("2. 然后，我需要分析相关信息\n");
            prompt.append("3. 接下来，我需要应用逻辑推理\n");
            prompt.append("4. 最后，我得出结论\n\n");
        }
        
        // 4. 输出要求
        prompt.append("请按照以上步骤思考，并在每一步中展示你的推理过程。\n");
        prompt.append("最后，请给出你的最终答案。\n");
        prompt.append("格式：\n");
        prompt.append("步骤1：[你的思考]\n");
        prompt.append("步骤2：[你的思考]\n");
        prompt.append("步骤3：[你的思考]\n");
        prompt.append("最终答案：[你的答案]");
        
        return prompt.toString();
    }
    
    /**
     * 构建带Few-shot示例的CoT Prompt
     * 
     * @param question 问题
     * @param context 上下文信息
     * @param examples Few-shot示例（包含推理过程）
     * @return CoT Prompt with Few-shot
     */
    public String buildCoTPromptWithExamples(
            String question, 
            Map<String, Object> context, 
            List<CoTExample> examples) {
        
        StringBuilder prompt = new StringBuilder();
        
        // 1. Few-shot示例
        if (examples != null && !examples.isEmpty()) {
            prompt.append("以下是一些示例，展示了如何一步步思考：\n\n");
            
            for (int i = 0; i < examples.size(); i++) {
                CoTExample example = examples.get(i);
                prompt.append("示例").append(i + 1).append("：\n");
                prompt.append("问题：").append(example.getQuestion()).append("\n");
                
                // 展示推理步骤
                if (example.getSteps() != null && !example.getSteps().isEmpty()) {
                    for (int j = 0; j < example.getSteps().size(); j++) {
                        prompt.append(String.format("步骤%d：%s\n", j + 1, example.getSteps().get(j)));
                    }
                }
                
                prompt.append("最终答案：").append(example.getAnswer()).append("\n\n");
            }
        }
        
        // 2. 当前问题
        prompt.append("现在，请按照同样的方式思考以下问题：\n\n");
        prompt.append(buildCoTPrompt(question, context));
        
        return prompt.toString();
    }
    
    /**
     * 构建简化版CoT Prompt（只要求展示关键步骤）
     * 
     * @param question 问题
     * @param context 上下文信息
     * @return 简化版CoT Prompt
     */
    public String buildSimpleCoTPrompt(String question, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("问题：").append(question).append("\n\n");
        
        if (context != null && !context.isEmpty()) {
            prompt.append("相关信息：\n");
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ")
                      .append(entry.getValue()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("请一步步思考并回答。\n");
        prompt.append("格式：\n");
        prompt.append("思考过程：[你的推理]\n");
        prompt.append("答案：[你的答案]");
        
        return prompt.toString();
    }
    
    /**
     * Chain-of-Thought示例
     */
    @lombok.Data
    @lombok.Builder
    public static class CoTExample {
        /**
         * 问题
         */
        private String question;
        
        /**
         * 推理步骤
         */
        private List<String> steps;
        
        /**
         * 最终答案
         */
        private String answer;
    }
}
