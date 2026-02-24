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
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地大模型服务
 *
 * 使用 LangChain4j 的 Ollama 集成调用本地部署的 Ollama 模型。
 * 本类使用 Java 8+ 特性（ConcurrentHashMap、computeIfAbsent、Duration、Optional、List.of），旧写法以注释保留在「对比学习」块中。
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
     * 调用 Ollama 的 HTTP 超时时间（毫秒）。多模态/大模型推理较慢，建议 60s 以上。
     */
    @Value("${local-llm.ollama.timeout:300000}")
    private long ollamaTimeoutMs;

    /**
     * ObjectMapper 用于解析 JSON
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 缓存的 ChatLanguageModel 实例（按模型名称缓存）。
     * ----- 对比学习：若不用并发需求可用 HashMap，但多线程下需外部加锁。
     * ----- 新写法 + 特性含义：ConcurrentHashMap（Java 8）线程安全的 HashMap，支持高并发读写，
     * 内部细粒度锁，无需在调用处 synchronized，适合缓存。
     */
    private final Map<String, ChatLanguageModel> modelCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final Object modelLock = new Object();

    /**
     * 推理结果（包含内容和 Token 使用信息）。
     * ----- 对比学习：旧写法为 @Data class + 私有字段 + Lombok 生成 getter/setter。
     * ----- 新写法 + 特性含义：record（Java 16+）不可变数据载体，一条声明即生成规范构造器与访问器（content()、inputTokens() 等），适合返回值 DTO。
     */
    public record InferenceResult(
        String content,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String ocrText   // OCR 识别结果（用于返回给前端显示）
    ) {}

    /**
     * 调用本地 Ollama 模型进行推理（支持多图）
     *
     * @param prompt   提示词
     * @param imageUrls 图片URL列表（可选，支持多张图片）
     * @param ocrText  OCR文本（可选）
     * @param modelName 模型名称（可选，如果为空则使用默认模型）
     * @return 模型推理结果（包含内容和Token信息）
     */
    public InferenceResult infer(String prompt, List<String> imageUrls, String ocrText, String modelName) {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        // 如果没有指定模型，使用默认模型
        String actualModelName = (modelName != null && !modelName.isEmpty())
            ? modelName
            : defaultModelName;

        int imageCount = (imageUrls != null) ? imageUrls.size() : 0;
        log.info("调用本地Ollama模型: promptLength={}, imageCount={}, hasOcrText={}, model={}",
            prompt.length(),
            imageCount,
            ocrText != null && !ocrText.isEmpty(),
            actualModelName);

        try {
            ChatLanguageModel model = getOrCreateModel(actualModelName);

            // 构建多模态消息内容
            List<Content> contents = new ArrayList<>();

            // 1. 添加文本内容（包含OCR文本和提示词）
            String fullPrompt = buildFullPrompt(prompt, ocrText);
            contents.add(TextContent.from(fullPrompt));

            // 2. 添加多张图片内容（如果提供）
            // 支持格式：HTTP/HTTPS URL、file://、Base64 Data URL
            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (int i = 0; i < imageUrls.size(); i++) {
                    String imageUrl = imageUrls.get(i);
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        continue;
                    }
                    try {
                        contents.add(ImageContent.from(imageUrl));
                        log.debug("添加图片 {}: dataUrl={}, length={}", i + 1,
                            imageUrl.startsWith("data:"), imageUrl.length());
                    } catch (Exception e) {
                        log.warn("添加图片失败，跳过: index={}, error={}", i + 1, e.getMessage());
                    }
                }
            }

            // 3. 创建多模态消息（文本 + 图片）
            UserMessage userMessage = UserMessage.userMessage(contents);

            long start = System.currentTimeMillis();
            // ----- 对比学习：旧写法 可 new ArrayList<>() 再 add(userMessage)，再传入。 -----
            // ----- 新写法 + 特性含义：List.of（Java 9+）不可变 List 工厂，创建后不可修改，单元素列表语义清晰 -----
            Response<AiMessage> response = model.generate(List.of(userMessage));
            long cost = System.currentTimeMillis() - start;

            // 提取内容
            String content = response.content().text();

            // ----- 对比学习：旧写法 -----
            // TokenUsage tokenUsage = response.tokenUsage();
            // Integer inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : null;
            // ...
            // ----- 新写法 + 特性含义：Optional（Java 8）表示「可能为空」的容器；
            // ofNullable 包装可能 null 的值，map 对非空做转换，orElse(null) 为空时返回 null，避免重复 if (x != null) -----
            TokenUsage tokenUsage = response.tokenUsage();
            Integer inputTokens = Optional.ofNullable(tokenUsage).map(TokenUsage::inputTokenCount).orElse(null);
            Integer outputTokens = Optional.ofNullable(tokenUsage).map(TokenUsage::outputTokenCount).orElse(null);
            Integer totalTokens = Optional.ofNullable(tokenUsage).map(TokenUsage::totalTokenCount).orElse(null);

            int resultLen = content != null ? content.length() : 0;
            log.info("本地Ollama推理完成: cost={}ms, resultLength={}, inputTokens={}, outputTokens={}, totalTokens={}",
                cost, resultLen, inputTokens, outputTokens, totalTokens);
            if (resultLen == 0) {
                log.warn("模型返回内容为空，可能原因：提示/图片触发了安全策略、模型无法理解、或 Ollama 响应格式异常，可尝试换模型或简化输入。");
            }

            // ----- 对比学习：旧写法 -----
            // InferenceResult result = new InferenceResult();
            // result.setContent(content); result.setInputTokens(...); ...
            // ----- 新写法：record 使用规范构造器一次性创建不可变对象 -----
            String resultOcrText = (ocrText != null && !ocrText.trim().isEmpty()) ? ocrText.trim() : null;
            return new InferenceResult(content, inputTokens, outputTokens, totalTokens, resultOcrText);
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
     * 懒加载并缓存 ChatLanguageModel 实例（按模型名称缓存）。
     * ----- 对比学习：旧写法 可 modelCache.get(name); if (null) { synchronized { 再 get 再 put } }，逻辑冗长。
     * ----- 新写法 + 特性含义：computeIfAbsent（Java 8）Map 的原子方法，键不存在时用 lambda 计算并放入，存在则直接返回值；
     * Duration.ofMillis（Java 8）表示「一段时长」的类型，比裸 long 毫秒数语义清晰，便于与 API 对接。
     */
    private ChatLanguageModel getOrCreateModel(String modelName) {
        return modelCache.computeIfAbsent(modelName, name -> {
            synchronized (modelLock) {
                if (modelCache.containsKey(name)) {
                    return modelCache.get(name);
                }
                log.info("初始化 Ollama ChatLanguageModel: baseUrl={}, model={}, timeoutMs={}", baseUrl, name, ollamaTimeoutMs);
                ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl(normalizeBaseUrl(baseUrl))
                    .modelName(name)
                    .timeout(Duration.ofMillis(ollamaTimeoutMs))
                    .build();
                return model;
            }
        });
    }

    /**
     * 构建完整的 Prompt（包含 OCR 文本）。
     * ----- 对比学习：旧写法 return String.format("%s%n%nOCR识别结果：%n%s", prompt, ocrText);
     * ----- 新写法 + 特性含义：文本块（Java 15+）"""...""" 多行字符串字面量，避免 %n 与转义，可读性更好。
     */
    private String buildFullPrompt(String prompt, String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return prompt;
        }
        return String.format("""
            %s

            OCR识别结果：
            %s
            """, prompt, ocrText);
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
