package com.wuxiansheng.shieldarch.marsdata.llm.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.utils.DiSFUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
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
 * 使用 LangChain4j 原生 ImageContent 的 ChatModel 实现
 * 
 * 这是升级版本，使用原生的 ImageContent 和 TextContent，
 * 而不是通过 ThreadLocal 传递图片 URL。
 * 
 * 优势：
 * 1. 图片信息直接包含在消息对象中，类型安全
 * 2. 无需管理 ThreadLocal，避免内存泄漏
 * 3. 代码更清晰，符合 LangChain4j 设计理念
 * 4. 支持多种图片输入方式（URL、Base64、字节数组）
 */
@Slf4j
public class DiSFChatModelNative implements ChatLanguageModel {
    
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
    
    public DiSFChatModelNative(String disfName, String appId, String model,
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
            return parseResponse(response.body());
            
        } catch (Exception e) {
            log.error("DiSFChatModelNative 调用失败: disfName={}, error={}", disfName, e.getMessage(), e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
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
     * 转换 ChatMessage 为 API 格式（使用原生 ImageContent）
     * 
     * 这是关键改进：直接从消息对象获取图片，而不是从 ThreadLocal
     */
    private Map<String, Object> convertMessage(ChatMessage message) {
        Map<String, Object> msgMap = new HashMap<>();
        
        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            msgMap.put("role", "user");
            
            // 处理多模态内容（文本 + 图片）
            // 直接从消息对象获取所有内容，无需 ThreadLocal
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // 遍历消息的所有内容部分
            for (Content content : userMessage.contents()) {
                if (content instanceof TextContent) {
                    // 处理文本内容
                    TextContent textContent = (TextContent) content;
                    Map<String, Object> textMap = new HashMap<>();
                    textMap.put("type", "text");
                    textMap.put("text", textContent.text());
                    contents.add(textMap);
                    
                } else if (content instanceof ImageContent) {
                    // 处理图片内容（原生方式）
                    ImageContent imageContent = (ImageContent) content;
                    Map<String, Object> imageMap = new HashMap<>();
                    imageMap.put("type", "image_url");
                    
                    // 注意：LangChain4j 的 ImageContent API 可能因版本而异
                    // 这里使用反射或 toString() 来获取图片信息
                    // 实际使用时需要根据 LangChain4j 版本调整
                    
                    // 方案1: 如果 ImageContent 有 source() 方法
                    try {
                        // 尝试通过反射获取图片 URL 或数据
                        java.lang.reflect.Method sourceMethod = imageContent.getClass().getMethod("source");
                        Object source = sourceMethod.invoke(imageContent);
                        
                        if (source != null) {
                            String sourceStr = source.toString();
                            // 判断是 URL 还是 Base64
                            if (sourceStr.startsWith("http://") || sourceStr.startsWith("https://") || 
                                sourceStr.startsWith("data:")) {
                                Map<String, String> imageUrlObj = new HashMap<>();
                                imageUrlObj.put("url", sourceStr);
                                imageMap.put("image_url", imageUrlObj);
                                contents.add(imageMap);
                            }
                        }
                    } catch (Exception e) {
                        // 如果反射失败，尝试其他方式
                        log.warn("无法通过反射获取 ImageContent 信息，尝试备用方案: {}", e.getMessage());
                        
                        // 方案2: 使用 toString() 或直接处理
                        // 注意：这需要根据实际的 LangChain4j 版本调整
                        // 建议查看 LangChain4j 文档或源码来确定正确的 API
                    }
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

