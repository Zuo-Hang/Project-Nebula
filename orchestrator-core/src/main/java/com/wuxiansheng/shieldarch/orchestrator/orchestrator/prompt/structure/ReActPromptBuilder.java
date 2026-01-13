package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.structure;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct模式Prompt构建器
 * 
 * 功能：
 * 1. 构建推理+行动模式的Prompt
 * 2. 支持多轮交互（思考-行动-观察循环）
 * 3. 提高AI的推理能力和行动能力
 * 
 * 参考：ReAct: Synergizing Reasoning and Acting in Language Models (Yao et al., 2022)
 */
@Slf4j
@Component
public class ReActPromptBuilder {
    
    /**
     * 构建ReAct Prompt
     * 
     * @param question 问题
     * @param context 上下文信息
     * @param tools 可用工具列表（可选）
     * @param history 历史交互记录（可选）
     * @return ReAct Prompt
     */
    public String buildReActPrompt(
            String question, 
            Map<String, Object> context, 
            List<Tool> tools, 
            List<ReActStep> history) {
        
        StringBuilder prompt = new StringBuilder();
        
        // 1. 系统指令
        prompt.append("你是一个智能助手，可以使用工具来解决问题。\n");
        prompt.append("请按照以下格式思考和行动：\n\n");
        
        // 2. 格式说明
        prompt.append("格式：\n");
        prompt.append("思考：[你的推理过程]\n");
        prompt.append("行动：[工具名称](参数)\n");
        prompt.append("观察：[工具返回的结果]\n");
        prompt.append("...（重复思考-行动-观察循环，直到解决问题）\n");
        prompt.append("最终答案：[你的最终答案]\n\n");
        
        // 3. 可用工具（如果有）
        if (tools != null && !tools.isEmpty()) {
            prompt.append("可用工具：\n");
            for (Tool tool : tools) {
                prompt.append(String.format("- %s: %s\n", tool.getName(), tool.getDescription()));
                if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                    prompt.append("  参数：").append(tool.getParameters()).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 4. 上下文信息（如果有）
        if (context != null && !context.isEmpty()) {
            prompt.append("上下文信息：\n");
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ")
                      .append(entry.getValue()).append("\n");
            }
            prompt.append("\n");
        }
        
        // 5. 历史交互记录（如果有）
        if (history != null && !history.isEmpty()) {
            prompt.append("之前的交互记录：\n");
            for (int i = 0; i < history.size(); i++) {
                ReActStep step = history.get(i);
                prompt.append(String.format("步骤%d：\n", i + 1));
                if (step.getThought() != null) {
                    prompt.append("思考：").append(step.getThought()).append("\n");
                }
                if (step.getAction() != null) {
                    prompt.append("行动：").append(step.getAction()).append("\n");
                }
                if (step.getObservation() != null) {
                    prompt.append("观察：").append(step.getObservation()).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        // 6. 当前问题
        prompt.append("问题：").append(question).append("\n\n");
        prompt.append("请开始思考和行动：\n");
        
        return prompt.toString();
    }
    
    /**
     * 构建简化版ReAct Prompt（不需要工具）
     * 
     * @param question 问题
     * @param context 上下文信息
     * @return 简化版ReAct Prompt
     */
    public String buildSimpleReActPrompt(String question, Map<String, Object> context) {
        return buildReActPrompt(question, context, null, null);
    }
    
    /**
     * 构建带Few-shot示例的ReAct Prompt
     * 
     * @param question 问题
     * @param context 上下文信息
     * @param tools 可用工具列表
     * @param examples Few-shot示例
     * @return ReAct Prompt with Few-shot
     */
    public String buildReActPromptWithExamples(
            String question, 
            Map<String, Object> context, 
            List<Tool> tools, 
            List<ReActExample> examples) {
        
        StringBuilder prompt = new StringBuilder();
        
        // 1. Few-shot示例
        if (examples != null && !examples.isEmpty()) {
            prompt.append("以下是一些示例，展示了如何思考和行动：\n\n");
            
            for (int i = 0; i < examples.size(); i++) {
                ReActExample example = examples.get(i);
                prompt.append("示例").append(i + 1).append("：\n");
                prompt.append("问题：").append(example.getQuestion()).append("\n");
                
                // 展示交互步骤
                if (example.getSteps() != null && !example.getSteps().isEmpty()) {
                    for (int j = 0; j < example.getSteps().size(); j++) {
                        ReActStep step = example.getSteps().get(j);
                        prompt.append(String.format("步骤%d：\n", j + 1));
                        if (step.getThought() != null) {
                            prompt.append("思考：").append(step.getThought()).append("\n");
                        }
                        if (step.getAction() != null) {
                            prompt.append("行动：").append(step.getAction()).append("\n");
                        }
                        if (step.getObservation() != null) {
                            prompt.append("观察：").append(step.getObservation()).append("\n");
                        }
                    }
                }
                
                prompt.append("最终答案：").append(example.getAnswer()).append("\n\n");
            }
        }
        
        // 2. 当前问题
        prompt.append("现在，请按照同样的方式思考和行动：\n\n");
        prompt.append(buildReActPrompt(question, context, tools, null));
        
        return prompt.toString();
    }
    
    /**
     * ReAct步骤
     */
    @Data
    public static class ReActStep {
        /**
         * 思考
         */
        private String thought;
        
        /**
         * 行动
         */
        private String action;
        
        /**
         * 观察
         */
        private String observation;
    }
    
    /**
     * 工具
     */
    @Data
    @lombok.Builder
    public static class Tool {
        /**
         * 工具名称
         */
        private String name;
        
        /**
         * 工具描述
         */
        private String description;
        
        /**
         * 参数说明
         */
        private String parameters;
    }
    
    /**
     * ReAct示例
     */
    @Data
    @lombok.Builder
    public static class ReActExample {
        /**
         * 问题
         */
        private String question;
        
        /**
         * 交互步骤
         */
        private List<ReActStep> steps;
        
        /**
         * 最终答案
         */
        private String answer;
    }
}
