package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.template;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt链
 * 
 * 功能：
 * 1. 支持链式组合多个Prompt
 * 2. 支持Pipeline模式
 * 3. 支持条件分支
 * 
 * 参考：LangChain Pipeline
 */
@Slf4j
@Component
public class PromptChain {
    
    @Autowired(required = false)
    private com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager promptManager;
    
    /**
     * 构建Prompt链
     * 
     * @param stages Prompt阶段列表
     * @param bizType 业务类型
     * @param context 上下文变量
     * @return 组合后的Prompt
     */
    public String buildChain(
            List<PromptStage> stages, 
            String bizType, 
            Map<String, Object> context) {
        
        if (stages == null || stages.isEmpty()) {
            log.warn("Prompt阶段列表为空，返回空字符串");
            return "";
        }
        
        if (promptManager == null) {
            log.warn("PromptManager未配置，无法构建Prompt链");
            return "";
        }
        
        // 依次构建每个阶段的Prompt
        List<String> prompts = stages.stream()
            .map(stage -> promptManager.buildPrompt(bizType, stage, context))
            .collect(Collectors.toList());
        
        // 组合所有Prompt
        String combinedPrompt = String.join("\n\n", prompts);
        
        log.debug("构建Prompt链完成: bizType={}, stageCount={}, totalLength={}", 
            bizType, stages.size(), combinedPrompt.length());
        
        return combinedPrompt;
    }
    
    /**
     * 构建带条件分支的Prompt链
     * 
     * @param chainConfig 链配置
     * @param bizType 业务类型
     * @param context 上下文变量
     * @return 组合后的Prompt
     */
    public String buildConditionalChain(
            ChainConfig chainConfig, 
            String bizType, 
            Map<String, Object> context) {
        
        if (chainConfig == null || chainConfig.getStages() == null || chainConfig.getStages().isEmpty()) {
            return "";
        }
        
        List<String> prompts = new ArrayList<>();
        
        for (ChainStage stage : chainConfig.getStages()) {
            // 检查条件（如果有）
            if (stage.getCondition() != null) {
                boolean conditionMet = evaluateCondition(stage.getCondition(), context);
                if (!conditionMet) {
                    log.debug("条件不满足，跳过阶段: stage={}, condition={}", 
                        stage.getStage(), stage.getCondition());
                    continue;
                }
            }
            
            // 构建该阶段的Prompt
            String prompt = promptManager.buildPrompt(bizType, stage.getStage(), context);
            prompts.add(prompt);
        }
        
        String combinedPrompt = String.join("\n\n", prompts);
        
        log.debug("构建条件Prompt链完成: bizType={}, stageCount={}, totalLength={}", 
            bizType, chainConfig.getStages().size(), combinedPrompt.length());
        
        return combinedPrompt;
    }
    
    /**
     * 评估条件
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        // 简化实现：检查变量是否存在且非空
        // 实际应该支持更复杂的条件表达式
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        
        // 支持简单的条件：variable 或 !variable
        if (condition.startsWith("!")) {
            String variable = condition.substring(1).trim();
            Object value = context.get(variable);
            return value == null || (value instanceof String && ((String) value).isEmpty());
        } else {
            Object value = context.get(condition.trim());
            return value != null && !(value instanceof String && ((String) value).isEmpty());
        }
    }
    
    /**
     * 链配置
     */
    @Data
    public static class ChainConfig {
        /**
         * Prompt阶段列表
         */
        private List<ChainStage> stages = new ArrayList<>();
    }
    
    /**
     * 链阶段
     */
    @Data
    public static class ChainStage {
        /**
         * Prompt阶段
         */
        private PromptStage stage;
        
        /**
         * 条件（可选）
         */
        private String condition;
    }
}
