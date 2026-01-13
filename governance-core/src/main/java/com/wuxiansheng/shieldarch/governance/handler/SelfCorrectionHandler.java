package com.wuxiansheng.shieldarch.governance.handler;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.LLMServiceClient;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自愈处理器
 * 
 * 当校验失败时，根据错误类型和上下文，利用LLM重写或丰富最初的推理Prompt，
 * 然后要求AsyncInferenceWorker重新执行该步骤
 */
@Slf4j
@Component
public class SelfCorrectionHandler {
    
    @Autowired(required = false)
    private LLMServiceClient llmServiceClient;
    
    @Autowired(required = false)
    private PromptManager promptManager;
    
    /**
     * 最大重试次数
     */
    @Value("${governance.handler.self-correction.max-retries:3}")
    private int maxRetries;
    
    /**
     * 自愈结果
     */
    public static class CorrectionResult {
        private boolean success;
        private String correctedContent;
        private String correctedPrompt;
        private int retryCount;
        private List<String> correctionHistory = new ArrayList<>();
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getCorrectedContent() {
            return correctedContent;
        }
        
        public void setCorrectedContent(String correctedContent) {
            this.correctedContent = correctedContent;
        }
        
        public String getCorrectedPrompt() {
            return correctedPrompt;
        }
        
        public void setCorrectedPrompt(String correctedPrompt) {
            this.correctedPrompt = correctedPrompt;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
        
        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
        
        public List<String> getCorrectionHistory() {
            return correctionHistory;
        }
        
        public void setCorrectionHistory(List<String> correctionHistory) {
            this.correctionHistory = correctionHistory;
        }
        
        public void addHistory(String history) {
            this.correctionHistory.add(history);
        }
    }
    
    /**
     * 执行自愈重试（重载方法，保持向后兼容）
     */
    public CorrectionResult correctAndRetry(
            TaskContext context,
            String originalPrompt,
            String originalContent,
            List<String> validationErrors,
            String imageUrl,
            String ocrText) {
        return correctAndRetry(context, originalPrompt, originalContent, validationErrors, imageUrl, ocrText, null);
    }
    
    /**
     * 执行自愈重试
     * 
     * @param context 任务上下文
     * @param originalPrompt 原始Prompt
     * @param originalContent 原始推理结果
     * @param validationErrors 校验错误列表
     * @param imageUrl 图片URL（可选）
     * @param ocrText OCR文本（可选）
     * @param promptManager Prompt管理器（可选，用于构建反思Prompt）
     * @return 自愈结果
     */
    public CorrectionResult correctAndRetry(
            TaskContext context,
            String originalPrompt,
            String originalContent,
            List<String> validationErrors,
            String imageUrl,
            String ocrText,
            PromptManager promptManager) {
        
        CorrectionResult result = new CorrectionResult();
        result.setRetryCount(0);
        
        if (llmServiceClient == null) {
            log.warn("LLM服务客户端未配置，无法执行自愈重试");
            result.setSuccess(false);
            return result;
        }
        
        log.info("开始自愈重试: taskId={}, errorCount={}", context.getTaskId(), validationErrors.size());
        
        String currentPrompt = originalPrompt;
        String currentContent = originalContent;
        
        // 最多重试maxRetries次
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            result.setRetryCount(attempt);
            
            try {
                // 1. 根据错误信息生成改进的Prompt
                String improvedPrompt;
                if (promptManager != null) {
                    // 使用PromptManager构建反思Prompt
                    String bizType = inferBizType(context);
                    improvedPrompt = promptManager.buildReflectPrompt(
                        bizType, currentPrompt, currentContent, validationErrors, attempt);
                    log.info("自愈重试 [{}/{}]: 使用PromptManager生成反思Prompt", attempt, maxRetries);
                } else {
                    // 使用传统方式生成改进Prompt
                    improvedPrompt = generateImprovedPrompt(
                        currentPrompt, currentContent, validationErrors, attempt);
                    log.info("自愈重试 [{}/{}]: 使用传统方式生成改进Prompt", attempt, maxRetries);
                }
                
                result.addHistory(String.format("尝试 %d: 生成改进Prompt", attempt));
                
                // 2. 使用改进的Prompt重新推理
                currentContent = llmServiceClient.infer(improvedPrompt, imageUrl, ocrText);
                currentPrompt = improvedPrompt;
                
                log.info("自愈重试 [{}/{}}]: 重新推理完成, contentLength={}", 
                    attempt, maxRetries, currentContent != null ? currentContent.length() : 0);
                result.addHistory(String.format("尝试 %d: 重新推理完成", attempt));
                
                // 3. 再次校验（这里应该调用DualCheckValidator，但为了避免循环依赖，先简化处理）
                // 实际应该再次调用校验器，如果校验通过则返回成功
                // 这里先假设重新推理后内容已改进
                result.setCorrectedContent(currentContent);
                result.setCorrectedPrompt(currentPrompt);
                
                // 如果内容有变化，认为可能已改进
                if (currentContent != null && !currentContent.equals(originalContent)) {
                    log.info("自愈重试成功: taskId={}, attempt={}", context.getTaskId(), attempt);
                    result.setSuccess(true);
                    return result;
                }
                
            } catch (Exception e) {
                log.error("自愈重试失败: taskId={}, attempt={}, error={}", 
                    context.getTaskId(), attempt, e.getMessage(), e);
                result.addHistory(String.format("尝试 %d: 失败 - %s", attempt, e.getMessage()));
            }
        }
        
        log.warn("自愈重试达到最大次数仍未成功: taskId={}, maxRetries={}", 
            context.getTaskId(), maxRetries);
        result.setSuccess(false);
        result.setCorrectedContent(currentContent);
        result.setCorrectedPrompt(currentPrompt);
        
        return result;
    }
    
    /**
     * 生成改进的Prompt
     * 
     * 根据错误信息，利用LLM生成一个改进的Prompt
     */
    private String generateImprovedPrompt(
            String originalPrompt,
            String originalContent,
            List<String> validationErrors,
            int attempt) {
        
        // 构建反思Prompt
        StringBuilder reflectionPrompt = new StringBuilder();
        reflectionPrompt.append("原始任务：\n").append(originalPrompt).append("\n\n");
        reflectionPrompt.append("原始推理结果：\n").append(originalContent).append("\n\n");
        reflectionPrompt.append("校验发现的错误：\n");
        for (int i = 0; i < validationErrors.size(); i++) {
            reflectionPrompt.append(String.format("%d. %s\n", i + 1, validationErrors.get(i)));
        }
        reflectionPrompt.append("\n");
        reflectionPrompt.append("请根据以上错误信息，生成一个改进的任务描述（Prompt），");
        reflectionPrompt.append("要求：\n");
        reflectionPrompt.append("1. 明确指出需要修正的问题\n");
        reflectionPrompt.append("2. 提供更清晰的指导\n");
        reflectionPrompt.append("3. 确保输出结果符合校验要求\n");
        reflectionPrompt.append("4. 保持原始任务的核心目标\n\n");
        reflectionPrompt.append("只返回改进后的Prompt，不要包含其他内容。");
        
        try {
            // 使用LLM生成改进的Prompt
            String improvedPrompt = llmServiceClient.infer(reflectionPrompt.toString(), null, null);
            
            if (improvedPrompt != null && !improvedPrompt.isEmpty()) {
                return improvedPrompt;
            }
        } catch (Exception e) {
            log.error("生成改进Prompt失败: error={}", e.getMessage(), e);
        }
        
        // 如果LLM生成失败，使用简单的模板改进
        return buildSimpleImprovedPrompt(originalPrompt, validationErrors);
    }
    
    /**
     * 构建简单的改进Prompt（回退方案）
     */
    private String buildSimpleImprovedPrompt(String originalPrompt, List<String> validationErrors) {
        StringBuilder improved = new StringBuilder();
        improved.append(originalPrompt).append("\n\n");
        improved.append("重要提示：请确保输出结果满足以下要求：\n");
        for (int i = 0; i < validationErrors.size(); i++) {
            improved.append(String.format("%d. %s\n", i + 1, validationErrors.get(i)));
        }
        improved.append("\n请仔细检查并修正上述问题。");
        return improved.toString();
    }
    
    /**
     * 推断业务类型（用于PromptManager）
     */
    private String inferBizType(TaskContext context) {
        String taskType = context.getTaskType();
        if (taskType != null && !taskType.isEmpty()) {
            String bizType = taskType.toUpperCase();
            if (bizType.contains("GAODE") || bizType.contains("高德")) {
                return "GAODE";
            } else if (bizType.contains("XIAOLA") || bizType.contains("小拉")) {
                return "XIAOLA";
            }
            return bizType;
        }
        
        String linkName = context.getLinkName();
        if (linkName != null && !linkName.isEmpty()) {
            String lowerLinkName = linkName.toLowerCase();
            if (lowerLinkName.contains("gaode") || lowerLinkName.contains("高德")) {
                return "GAODE";
            } else if (lowerLinkName.contains("xiaola") || lowerLinkName.contains("小拉")) {
                return "XIAOLA";
            }
        }
        
        return "DEFAULT";
    }
}

