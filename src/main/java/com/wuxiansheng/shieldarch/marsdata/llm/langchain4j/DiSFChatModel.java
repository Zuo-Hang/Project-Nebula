package com.wuxiansheng.shieldarch.marsdata.llm.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.utils.DiSFUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 ChatModel，适配 DiSF 服务发现和现有的 LLM 服务
 * 
 * 将 LangChain4j 的 ChatLanguageModel 接口适配到现有的 DiSF + HTTP 调用方式
 */
@Slf4j
public class DiSFChatModel implements ChatLanguageModel {
    
    private final String disfName;
    private final String appId;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final double topK;
    private final double topP;
    private final double repetitionPenalty;
    private final boolean stream;
    
    private final DiSFUtils diSFUtils;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    
    public DiSFChatModel(String disfName, String appId, String model,
                        int maxTokens, double temperature, double topK, 
                        double topP, double repetitionPenalty, boolean stream,
                        DiSFUtils diSFUtils, ObjectMapper objectMapper) {
        this.disfName = disfName;
        this.appId = appId;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.repetitionPenalty = repetitionPenalty;
        this.stream = stream;
        this.diSFUtils = diSFUtils;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            // 1. 获取 HTTP 端点
            String endpoint = getHttpEndpoint();
            if (endpoint == null || endpoint.isEmpty()) {
                throw new RuntimeException("无法获取 LLM 端点: " + disfName);
            }
            
            String url = "http://" + endpoint + "/v1/chat/completions";
            
            // 2. 构建请求体（OpenAI 兼容格式）
            Map<String, Object> requestBody = buildRequestBody(messages);
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // 3. 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Luban-LLM-Service-APPId", appId)
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .timeout(Duration.ofSeconds(60));
            
            HttpRequest httpRequest = requestBuilder.build();
            
            // 4. 发送请求
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("LLM 请求失败: statusCode=" + response.statusCode() + 
                    ", body=" + response.body());
            }
            
            // 5. 解析响应
            Response<AiMessage> result = parseResponse(response.body());
            
            // 6. 清除上下文
            clearContext();
            
            return result;
            
        } catch (Exception e) {
            // 确保清除上下文
            clearContext();
            log.error("DiSFChatModel 调用失败: disfName={}, error={}", disfName, e.getMessage(), e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成方法（支持图片 URL）
     * 
     * @param prompt 提示词
     * @param imageUrl 图片 URL
     * @return LLM 响应
     */
    public Response<AiMessage> generateWithImage(String prompt, String imageUrl) {
        try {
            // 设置图片 URL（通过 ThreadLocal 传递）
            if (imageUrl != null && !imageUrl.isEmpty()) {
                setImageUrl(imageUrl);
            }
            
            // 构建用户消息
            UserMessage userMessage = UserMessage.userMessage(prompt);
            
            // 调用生成
            return generate(List.of(userMessage));
        } finally {
            // 确保清除上下文
            clearContext();
        }
    }
    
    /**
     * 获取 HTTP 端点
     */
    private String getHttpEndpoint() {
        if (disfName == null || disfName.isEmpty()) {
            log.warn("DiSF 服务名称为空");
            return null;
        }
        
        // 兼容测试环境的 VIP（不包含 disf! 前缀）
        if (!disfName.contains("disf!")) {
            log.debug("使用测试环境 VIP: {}", disfName);
            return disfName;
        }
        
        // 使用 DiSF 工具类获取服务端点
        if (diSFUtils != null) {
            String endpoint = diSFUtils.getHttpEndpoint(disfName);
            if (endpoint != null && !endpoint.isEmpty()) {
                return endpoint;
            }
        }
        
        log.warn("DiSF 服务发现不可用，无法获取端点: {}", disfName);
        return null;
    }
    
    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("top_k", topK);
        requestBody.put("top_p", topP);
        requestBody.put("repetition_penalty", repetitionPenalty);
        requestBody.put("stream", stream);
        
        // 转换消息格式
        List<Map<String, Object>> messageList = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> msgMap = convertMessage(message);
            if (msgMap != null) {
                messageList.add(msgMap);
            }
        }
        requestBody.put("messages", messageList);
        
        return requestBody;
    }
    
    /**
     * 存储图片 URL（用于多模态消息）
     * 由于 LangChain4j 的 UserMessage 结构限制，我们通过这个 Map 传递图片 URL
     */
    private final ThreadLocal<Map<String, String>> imageUrlContext = new ThreadLocal<>();
    
    /**
     * 设置图片 URL（在多模态调用前设置）
     */
    public void setImageUrl(String imageUrl) {
        Map<String, String> context = imageUrlContext.get();
        if (context == null) {
            context = new HashMap<>();
            imageUrlContext.set(context);
        }
        context.put("imageUrl", imageUrl);
    }
    
    /**
     * 清除上下文
     */
    public void clearContext() {
        imageUrlContext.remove();
    }
    
    /**
     * 转换 ChatMessage 为 API 格式
     */
    private Map<String, Object> convertMessage(ChatMessage message) {
        Map<String, Object> msgMap = new HashMap<>();
        
        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            msgMap.put("role", "user");
            
            // 处理多模态内容（文本 + 图片）
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // 文本内容
            String text = userMessage.singleText();
            if (text != null && !text.isEmpty()) {
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", text);
                contents.add(textContent);
            }
            
            // 图片内容（从 ThreadLocal 中获取）
            Map<String, String> context = imageUrlContext.get();
            if (context != null && context.containsKey("imageUrl")) {
                String imageUrl = context.get("imageUrl");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Map<String, Object> imageContent = new HashMap<>();
                    imageContent.put("type", "image_url");
                    Map<String, String> imageUrlObj = new HashMap<>();
                    imageUrlObj.put("url", imageUrl);
                    imageContent.put("image_url", imageUrlObj);
                    contents.add(imageContent);
                }
            }
            
            if (contents.isEmpty()) {
                return null;
            }
            
            msgMap.put("content", contents);
            
        } else if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            msgMap.put("role", "assistant");
            msgMap.put("content", aiMessage.text());
        }
        
        return msgMap;
    }
    
    /**
     * 解析响应
     */
    @SuppressWarnings("unchecked")
    private Response<AiMessage> parseResponse(String responseBody) throws Exception {
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, 
            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM 响应中没有 choices");
        }
        
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        String content = (String) message.get("content");
        
        // 去除可能的 JSON 代码块标记
        if (content != null) {
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();
        }
        
        AiMessage aiMessage = AiMessage.from(content);
        
        return Response.from(aiMessage);
    }
}

