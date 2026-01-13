package com.wuxiansheng.shieldarch.stepexecutors.executors;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.LLMServiceClient;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepExecutor;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepResult;
import com.wuxiansheng.shieldarch.stepexecutors.io.OcrClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 推理执行器（OCR识别）
 * 对应旧项目的 OCRStage 和 ReasonService
 * 
 * 实现批量OCR识别和LLM推理
 */
@Slf4j
@Component
public class InferenceExecutor implements StepExecutor {

    @Autowired(required = false)
    private OcrClient ocrClient;

    @Autowired(required = false)
    private LLMServiceClient llmServiceClient;
    
    @Autowired(required = false)
    private LangChain4jLLMServiceClient langChain4jLLMServiceClient;
    
    // 保持向后兼容的接口定义
    @Deprecated
    public interface LLMServiceClient extends com.wuxiansheng.shieldarch.orchestrator.orchestrator.LLMServiceClient {
    }

    @Override
    public String getName() {
        return "Inference";
    }

    @Override
    public String getDescription() {
        return "Batch OCR recognition and LLM inference";
    }

    @Override
    public CompletableFuture<StepResult> execute(TaskContext context, StepRequest request) throws Exception {
        // 从上下文获取图片路径
        List<String> imagePaths = context.getImagePaths();
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new Exception("InferenceExecutor: ImagePaths is required in context (当前为空)");
        }

        log.info("批量OCR识别开始，共 {} 张图片", imagePaths.size());

        // 1. OCR识别
        Map<String, String> ocrTextByImage = new HashMap<>();
        if (ocrClient != null) {
            try {
                // 调用OCR客户端进行批量识别
                Map<String, com.wuxiansheng.shieldarch.stepexecutors.io.AliResult> ocrResults = 
                    ocrClient.recognizeFilesOnce(imagePaths);
                
                // 将AliResult转换为String（OCR文本）
                for (Map.Entry<String, com.wuxiansheng.shieldarch.stepexecutors.io.AliResult> entry : ocrResults.entrySet()) {
                    com.wuxiansheng.shieldarch.stepexecutors.io.AliResult result = entry.getValue();
                    String text = "";
                    if (result != null && result.getOcrData() != null && !result.getOcrData().isEmpty()) {
                        text = String.join("\n", result.getOcrData());
                    }
                    ocrTextByImage.put(entry.getKey(), text);
                }
            } catch (Exception e) {
                log.error("OCR识别失败", e);
                throw new Exception("OCR识别失败: " + e.getMessage(), e);
            }
        } else {
            log.warn("OcrClient 未配置，跳过OCR识别");
        }

        // 验证识别结果
        int successCount = 0;
        int emptyCount = 0;
        for (Map.Entry<String, String> entry : ocrTextByImage.entrySet()) {
            String text = entry.getValue();
            if (text != null && !text.trim().isEmpty()) {
                successCount++;
            } else {
                emptyCount++;
            }
        }

        log.info("OCR识别完成: 成功 {} 张，空结果 {} 张，总计 {} 张", 
            successCount, emptyCount, ocrTextByImage.size());

        // 2. LLM推理（如果有prompt）
        String content = null;
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            try {
                // 优先使用 LangChain4j 客户端
                LLMServiceClient client = langChain4jLLMServiceClient != null 
                    ? langChain4jLLMServiceClient : llmServiceClient;
                
                if (client != null) {
                    // 使用第一张图片进行推理（或根据业务逻辑选择）
                    String imageUrl = imagePaths.get(0);
                    String ocrText = ocrTextByImage.getOrDefault(imageUrl, "");
                    
                    // 调用LLM服务
                    content = client.infer(request.getPrompt(), imageUrl, ocrText);
                    log.info("LLM推理完成: imageUrl={}, contentLength={}", imageUrl, content != null ? content.length() : 0);
                } else {
                    log.warn("LLM服务客户端未配置，跳过LLM推理");
                }
            } catch (Exception e) {
                log.error("LLM推理失败", e);
                // 不抛出异常，允许OCR结果继续传递
            }
        }

        // 3. 构建结果
        StepResult result = new StepResult();
        result.setOcrTextByImage(ocrTextByImage);
        result.setContent(content);
        result.setData(new HashMap<>());
        result.getData().put("successCount", successCount);
        result.getData().put("emptyCount", emptyCount);

        // 更新上下文
        context.setOcrTextByImage(ocrTextByImage);

        return CompletableFuture.completedFuture(result);
    }
}

