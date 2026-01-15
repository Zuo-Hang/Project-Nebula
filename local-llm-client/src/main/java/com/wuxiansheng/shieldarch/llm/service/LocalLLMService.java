package com.wuxiansheng.shieldarch.llm.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地大模型服务
 *
 * 使用 LangChain4j 的 Ollama 集成调用本地部署的 Ollama 模型。
 * 当前实现只使用文本通道（prompt + OCR 文本），imageUrl 暂未使用。
 */
@Slf4j
@Service
public class LocalLLMService {

    /**
     * Ollama 服务地址（默认 http://localhost:11434）
     */
    @Value("${local-llm.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    /**
     * 使用的模型名称（例如：qwen2.5vl:latest 或 llama3:latest）
     */
    @Value("${local-llm.ollama.model:qwen2.5vl:latest}")
    private String defaultModelName;

    /**
     * 可用模型列表（从配置文件读取，作为默认值）
     */
    @Value("${local-llm.ollama.available-models:qwen2.5vl:latest,llama3:latest}")
    private String[] defaultAvailableModels;

    /**
     * ObjectMapper 用于解析 JSON
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 缓存的 ChatLanguageModel 实例（按模型名称缓存）
     */
    private final Map<String, ChatLanguageModel> modelCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final Object modelLock = new Object();

    /**
     * 推理结果（包含内容和Token使用信息）
     */
    @Data
    public static class InferenceResult {
        private String content;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private String ocrText; // OCR识别结果（用于返回给前端显示）
    }

    /**
     * 调用本地 Ollama 模型进行推理
     *
     * @param prompt  提示词
     * @param imageUrl 图片URL（可选，用于多模态，当前暂未使用）
     * @param ocrText OCR文本（可选）
     * @param modelName 模型名称（可选，如果为空则使用默认模型）
     * @return 模型推理结果（包含内容和Token信息）
     */
    public InferenceResult infer(String prompt, String imageUrl, String ocrText, String modelName) {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        // 如果没有指定模型，使用默认模型
        String actualModelName = (modelName != null && !modelName.isEmpty()) 
            ? modelName 
            : defaultModelName;

        log.info("调用本地Ollama模型: promptLength={}, hasImage={}, hasOcrText={}, model={}",
            prompt.length(),
            imageUrl != null && !imageUrl.isEmpty(),
            ocrText != null && !ocrText.isEmpty(),
            actualModelName);

        try {
            ChatLanguageModel model = getOrCreateModel(actualModelName);

            // 构建多模态消息内容
            List<Content> contents = new ArrayList<>();
            
            // 1. 添加文本内容（包含OCR文本和提示词）
            String fullPrompt = buildFullPrompt(prompt, ocrText);
            contents.add(TextContent.from(fullPrompt));
            
            // 2. 添加图片内容（如果提供）
            // 支持两种格式：
            // - HTTP/HTTPS URL: http://localhost:9000/image.jpg
            // - Base64 Data URL: data:image/jpeg;base64,/9j/4AAQSkZJRg...
            if (imageUrl != null && !imageUrl.isEmpty()) {
                try {
                    if (imageUrl.startsWith("data:")) {
                        // Base64 格式：data:image/jpeg;base64,xxx
                        contents.add(ImageContent.from(imageUrl));
                        log.debug("使用Base64格式图片: dataUrlLength={}", imageUrl.length());
                    } else {
                        // URL 格式：http://... 或 file://...
                        contents.add(ImageContent.from(imageUrl));
                        log.debug("使用URL格式图片: imageUrl={}", imageUrl);
                    }
                } catch (Exception e) {
                    log.warn("添加图片内容失败，将仅使用文本: imageUrl={}, error={}", imageUrl, e.getMessage());
                    // 如果图片加载失败，继续使用纯文本模式
                }
            }

            // 3. 创建多模态消息（文本 + 图片）
            UserMessage userMessage = UserMessage.userMessage(contents);

            long start = System.currentTimeMillis();
            Response<AiMessage> response = model.generate(List.of(userMessage));
            long cost = System.currentTimeMillis() - start;

            // 提取内容
            String content = response.content().text();

            // 提取 Token 使用信息
            TokenUsage tokenUsage = response.tokenUsage();
            Integer inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : null;
            Integer outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : null;
            Integer totalTokens = tokenUsage != null ? tokenUsage.totalTokenCount() : null;

            log.info("本地Ollama推理完成: cost={}ms, resultLength={}, inputTokens={}, outputTokens={}, totalTokens={}",
                cost,
                content != null ? content.length() : 0,
                inputTokens,
                outputTokens,
                totalTokens);

            // 构建结果对象
            InferenceResult result = new InferenceResult();
            result.setContent(content);
            result.setInputTokens(inputTokens);
            result.setOutputTokens(outputTokens);
            result.setTotalTokens(totalTokens);
            // 保存OCR文本（只有当不为空时才保存，避免返回空字符串）
            result.setOcrText(
                (ocrText != null && !ocrText.trim().isEmpty()) ? ocrText.trim() : null
            );

            return result;
        } catch (Exception e) {
            log.error("本地Ollama推理失败", e);
            throw new RuntimeException("本地Ollama推理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 Ollama 服务是否可用
     *
     * @return true 表示服务可用，false 表示不可用
     */
    public boolean isServiceAvailable() {
        try {
            // 调用 Ollama 的 /api/tags 接口查看可用模型
            String url = normalizeBaseUrl(baseUrl) + "/api/tags";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            log.debug("Ollama 服务健康检查: url={}, status={}", url, response.getStatusCode());
            return ok;
        } catch (Exception e) {
            log.warn("Ollama 服务健康检查失败: baseUrl={}, error={}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用模型列表
     * 优先从 Ollama 动态获取，如果失败则使用配置文件中的默认值
     */
    public List<String> getAvailableModels() {
        try {
            // 尝试从 Ollama 动态获取模型列表
            String url = normalizeBaseUrl(baseUrl) + "/api/tags";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode models = root.get("models");
                
                if (models != null && models.isArray()) {
                    List<String> modelList = new ArrayList<>();
                    for (JsonNode model : models) {
                        JsonNode nameNode = model.get("name");
                        if (nameNode != null && nameNode.isTextual()) {
                            modelList.add(nameNode.asText());
                        }
                    }
                    
                    if (!modelList.isEmpty()) {
                        log.info("从Ollama动态获取到 {} 个模型: {}", modelList.size(), modelList);
                        return modelList;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从Ollama获取模型列表失败，使用配置文件默认值: {}", e.getMessage());
        }
        
        // 如果动态获取失败，使用配置文件中的默认值
        log.debug("使用配置文件中的默认模型列表: {}", Arrays.toString(defaultAvailableModels));
        return Arrays.asList(defaultAvailableModels);
    }

    /**
     * 懒加载并缓存 ChatLanguageModel 实例（按模型名称缓存）
     */
    private ChatLanguageModel getOrCreateModel(String modelName) {
        return modelCache.computeIfAbsent(modelName, name -> {
            synchronized (modelLock) {
                // 双重检查
                if (modelCache.containsKey(name)) {
                    return modelCache.get(name);
                }
                
                log.info("初始化 Ollama ChatLanguageModel: baseUrl={}, model={}", baseUrl, name);

                ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl(normalizeBaseUrl(baseUrl))
                    .modelName(name)
                    .build();
                
                return model;
            }
        });
    }

    /**
     * 构建完整的 Prompt（包含 OCR 文本）
     */
    private String buildFullPrompt(String prompt, String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return prompt;
        }
        return String.format("%s%n%nOCR识别结果：%n%s", prompt, ocrText);
    }

    /**
     * 规范化 baseUrl，去掉结尾的斜杠
     */
    private String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "http://localhost:11434";
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
