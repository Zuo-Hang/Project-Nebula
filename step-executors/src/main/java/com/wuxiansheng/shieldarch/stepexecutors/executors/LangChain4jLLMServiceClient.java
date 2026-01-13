package com.wuxiansheng.shieldarch.stepexecutors.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.orchestrator.config.AppConfigService;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClient;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.LLMServiceClient;
import com.wuxiansheng.shieldarch.orchestrator.utils.ServiceDiscovery;
import com.wuxiansheng.shieldarch.statestore.LLMCacheService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j LLM服务客户端实现
 * 参考旧项目的 LangChain4jLLMServiceNative 实现
 * 
 * 实现 InferenceExecutor.LLMServiceClient 接口
 */
@Slf4j
@Component
public class LangChain4jLLMServiceClient implements LLMServiceClient {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private AppConfigService appConfigService;
    
    @Autowired(required = false)
    private ServiceDiscovery serviceDiscovery;
    
    @Autowired(required = false)
    private LLMCacheService llmCacheService;
    
    @Autowired(required = false)
    private MetricsClient metricsClient;
    
    /**
     * ChatModel 缓存（按业务名称缓存）
     */
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    @Override
    public String infer(String prompt, String imageUrl, String ocrText) throws Exception {
        // 使用默认业务名称（可以从配置或上下文获取）
        String businessName = "default";
        
        // 1. 检查缓存
        if (llmCacheService != null && imageUrl != null && !imageUrl.isEmpty()) {
            LLMCacheService.LLMCacheResult cacheResult = llmCacheService.getLLMCache(
                imageUrl, businessName, prompt);
            
            if (cacheResult != null && llmCacheService.isLLMCacheValid(cacheResult, businessName)) {
                log.info("LLM缓存命中: imageUrl={}, business={}", imageUrl, businessName);
                return cacheResult.getContent();
            }
        }
        
        // 2. 获取或创建 ChatModel
        ChatLanguageModel model = getOrCreateChatModel(businessName);
        
        // 3. 构建内容列表（使用原生 ImageContent）
        List<Content> contents = new ArrayList<>();
        
        // 添加文本内容（包含OCR文本和提示词）
        String fullPrompt = buildFullPrompt(prompt, ocrText);
        contents.add(TextContent.from(fullPrompt));
        
        // 添加图片内容（如果提供）
        if (imageUrl != null && !imageUrl.isEmpty()) {
            contents.add(ImageContent.from(imageUrl));
        }
        
        // 4. 创建多模态消息（图片 + 文本）
        UserMessage userMessage = UserMessage.userMessage(contents);
        
        // 5. 调用 LLM
        Response<AiMessage> response = model.generate(List.of(userMessage));
        
        // 6. 提取内容
        String content = response.content().text();
        
        // 7. 提取并上报Token使用量
        reportTokenUsage(response, businessName);
        
        // 8. 缓存结果
        if (llmCacheService != null && imageUrl != null && !imageUrl.isEmpty() && content != null) {
            llmCacheService.setLLMCache(imageUrl, businessName, prompt, content);
        }
        
        log.debug("LangChain4j LLM 调用成功: business={}, contentLength={}", 
            businessName, content != null ? content.length() : 0);
        
        return content;
    }
    
    /**
     * 上报Token使用量指标
     */
    private void reportTokenUsage(Response<AiMessage> response, String businessName) {
        if (metricsClient == null) {
            return;
        }
        
        try {
            // 从Response中提取Token使用量
            TokenUsage tokenUsage = response.tokenUsage();
            
            if (tokenUsage != null) {
                int inputTokenCount = tokenUsage.inputTokenCount();
                int outputTokenCount = tokenUsage.outputTokenCount();
                int totalTokenCount = tokenUsage.totalTokenCount();
                
                // 上报指标
                Map<String, String> tags = new HashMap<>();
                tags.put("business", businessName);
                tags.put("type", "input");
                metricsClient.count("llm_token_usage", inputTokenCount, tags);
                
                tags.put("type", "output");
                metricsClient.count("llm_token_usage", outputTokenCount, tags);
                
                tags.put("type", "total");
                metricsClient.count("llm_token_usage", totalTokenCount, tags);
                
                log.debug("Token使用量已上报: business={}, input={}, output={}, total={}", 
                    businessName, inputTokenCount, outputTokenCount, totalTokenCount);
            } else {
                log.debug("Response中未包含Token使用量信息");
            }
        } catch (Exception e) {
            log.warn("上报Token使用量失败: business={}, error={}", businessName, e.getMessage());
        }
    }
    
    /**
     * 构建完整的提示词（包含OCR文本）
     */
    private String buildFullPrompt(String prompt, String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return prompt;
        }
        
        // 将OCR文本添加到提示词中
        return String.format("%s\n\nOCR识别结果：\n%s", prompt, ocrText);
    }
    
    /**
     * 获取或创建 ChatModel
     */
    private ChatLanguageModel getOrCreateChatModel(String businessName) {
        return modelCache.computeIfAbsent(businessName, name -> {
            // 获取 LLM 集群配置
            LLMClusterConf conf = getLLMClusterConf(name);
            
            // 创建 ChatModel（使用自定义实现，参考旧项目的 DiSFChatModelNative）
            // 注意：这里需要实现一个自定义的 ChatLanguageModel
            // 暂时返回一个占位实现，后续需要根据实际的服务发现机制实现
            return createChatModel(conf);
        });
    }
    
    /**
     * 创建 ChatModel
     * 注意：这里需要实现自定义的 ChatLanguageModel
     * 参考旧项目的 DiSFChatModelNative 实现
     * 由于需要服务发现和HTTP客户端，这里先返回占位实现
     * 后续可以根据实际需求实现完整的 ChatLanguageModel
     */
    private ChatLanguageModel createChatModel(LLMClusterConf conf) {
        // TODO: 实现自定义 ChatLanguageModel
        // 参考旧项目的 DiSFChatModelNative，需要：
        // 1. 使用 ServiceDiscovery 获取服务端点
        // 2. 构建 HTTP 请求（OpenAI 兼容格式）
        // 3. 处理多模态消息（TextContent + ImageContent）
        // 4. 解析响应
        
        log.warn("ChatModel创建未实现，使用占位实现。需要实现自定义ChatLanguageModel");
        log.info("LLM配置: serviceName={}, model={}, maxTokens={}", 
            conf.getServiceName(), conf.getParams().getModel(), conf.getParams().getMaxTokens());
        
        return new PlaceholderChatModel();
    }
    
    /**
     * 获取 LLM 集群配置
     */
    private LLMClusterConf getLLMClusterConf(String businessName) {
        LLMClusterConf defaultConf = getDefaultLLMClusterConf();
        
        if (appConfigService == null) {
            log.warn("AppConfigService未配置，使用默认配置");
            return defaultConf;
        }
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.warn("获取配置失败: {}, 使用默认配置", AppConfigService.OCR_LLM_CONF);
            return defaultConf;
        }
        
        String confKey = "llm_cluster_conf_" + businessName;
        String confStr = params.get(confKey);
        
        if (confStr == null || confStr.isEmpty()) {
            return defaultConf;
        }
        
        try {
            LLMClusterConf conf = objectMapper.readValue(confStr, LLMClusterConf.class);
            return conf;
        } catch (Exception e) {
            log.error("解析 LLM 集群配置失败: businessName={}, conf={}, error={}", 
                businessName, confStr, e.getMessage(), e);
            return defaultConf;
        }
    }
    
    /**
     * 获取默认 LLM 集群配置
     */
    private LLMClusterConf getDefaultLLMClusterConf() {
        LLMClusterConf conf = new LLMClusterConf();
        conf.setServiceName("llm-service");
        conf.setAppId("default-app-id");
        
        LLMClusterConf.LLMParams params = new LLMClusterConf.LLMParams();
        params.setModel("qwen2.5-vl");
        params.setMaxTokens(8192);
        params.setTemperature(0.3);
        params.setTopK(50);
        params.setTopP(0.8);
        params.setRepetitionPenalty(1.0);
        params.setStream(false);
        conf.setParams(params);
        
        return conf;
    }
    
    /**
     * 清除缓存（用于配置更新时）
     */
    public void clearCache() {
        modelCache.clear();
        log.info("LangChain4j ChatModel 缓存已清除");
    }
    
    /**
     * 清除指定业务的缓存
     */
    public void clearCache(String businessName) {
        modelCache.remove(businessName);
        log.info("LangChain4j ChatModel 缓存已清除: business={}", businessName);
    }
    
    /**
     * LLM集群配置
     */
    @Data
    public static class LLMClusterConf {
        private String serviceName;
        private String appId;
        private LLMParams params;
        
        @Data
        public static class LLMParams {
            private String model;
            private int maxTokens;
            private double temperature;
            private int topK;
            private double topP;
            private double repetitionPenalty;
            private boolean stream;
        }
    }
    
    /**
     * 占位 ChatModel 实现（需要替换为实际实现）
     */
    private static class PlaceholderChatModel implements ChatLanguageModel {
        @Override
        public Response<AiMessage> generate(List<dev.langchain4j.data.message.ChatMessage> messages) {
            throw new UnsupportedOperationException("PlaceholderChatModel未实现，需要实现自定义ChatLanguageModel");
        }
    }
}

