package com.wuxiansheng.shieldarch.marsdata.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.AppConfigService;
import com.wuxiansheng.shieldarch.marsdata.llm.langchain4j.LangChain4jLLMService;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.marsdata.utils.ServiceDiscovery;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 * LLM客户端
 */
@Slf4j
@Service
public class LLMClient {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Autowired
    private AppConfigService appConfigService;
    
    @Autowired(required = false)
    private ServiceDiscovery serviceDiscovery;
    
    @Autowired(required = false)
    private LangChain4jLLMService langChain4jLLMService;
    
    /**
     * 是否使用 LangChain4j（可配置，默认 true）
     */
    @Value("${llm.use-langchain4j:true}")
    private boolean useLangChain4j;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    /**
     * LLM集群配置
     */
    @Data
    public static class LLMClusterConf {
        private String serviceName;  // 服务名称（支持服务发现格式，如 "disf!service-name"）
        private String appId;
        private LLMParams params;
        
        @Data
        public static class LLMParams {
            private String model;
            private int maxTokens;
            private double temperature;
            private double topK;
            private double topP;
            private double repetitionPenalty;
            private boolean stream;
        }
    }
    
    /**
     * LLM请求
     */
    @Data
    public static class RequestLLMRequest {
        private String llmServiceName;  // LLM服务名称（支持服务发现格式）
        private String caller;
        private Map<String, String> headers;
        private String params;
        private String reqUrl;
        private String prompt;
    }
    
    /**
     * LLM响应
     */
    @Data
    public static class LLMResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<LLMResponseChoice> choices;
        private LLMResponseUsage usage;
        private String promptLogprobs;
        
        @Data
        public static class LLMResponseChoice {
            private int index;
            private LLMResponseMessage message;
            private String logprobs;
            private String finishReason;
            private String stopReason;
        }
        
        @Data
        public static class LLMResponseMessage {
            private String role;
            private String reasoningContent;
            private String content;
            private List<Object> toolCalls;
        }
        
        @Data
        public static class LLMResponseUsage {
            private int promptTokens;
            private int totalTokens;
            private int completionTokens;
            private String promptTokensDetails;
        }
    }
    
    /**
     * LLM请求消息
     */
    @Data
    public static class LLMRequest {
        private String model;
        private List<Object> messages;
        private int maxTokens;
        private double temperature;
        private double topK;
        private double topP;
        private double repetitionPenalty;
        private boolean stream;
    }
    
    /**
     * LLM消息
     */
    @Data
    public static class LLMMessage {
        private String role;
        private List<LLMMessageContent> content;
    }
    
    @Data
    public static class LLMMessageContent {
        private String type;
        private String text;
        private MessageImageUrl imageUrl;
    }
    
    @Data
    public static class MessageImageUrl {
        private String url;
    }
    
    /**
     * 创建LLM请求
     */
    public RequestLLMRequest newRequestLLMRequest(String businessName, String url, String prompt) {
        LLMClusterConf conf = getLLMClusterConf(businessName);
        
        RequestLLMRequest request = new RequestLLMRequest();
        request.setLlmServiceName(conf.getServiceName());
        request.setCaller(businessName);
        request.setReqUrl(url);
        request.setPrompt(prompt);
        
        // 设置Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Luban-LLM-Service-APPId", conf.getAppId());
        headers.put("Content-type", "application/json");
        request.setHeaders(headers);
        
        // 设置Params
        try {
            String paramsJson = objectMapper.writeValueAsString(conf.getParams());
            request.setParams(paramsJson);
        } catch (Exception e) {
            log.error("序列化LLM参数失败: businessName={}, error={}", businessName, e.getMessage(), e);
        }
        
        return request;
    }
    
    /**
     * 请求LLM（核心方法，使用 LangChain4j 重构）
     * 
     * @param request LLM请求
     * @return LLM响应
     */
    public LLMResponse requestLLM(RequestLLMRequest request) throws Exception {
        long beginTime = System.currentTimeMillis();
        Exception error = null;
        
        try {
            // 如果启用了 LangChain4j 且服务可用，使用 LangChain4j
            if (useLangChain4j && langChain4jLLMService != null) {
                return requestLLMWithLangChain4j(request, beginTime);
            }
            
            // 否则使用原有实现（向后兼容）
            return requestLLMLegacy(request, beginTime);
            
        } catch (Exception e) {
            error = e;
            log.error("requestLLM失败: pic_url={}, error={}", request.getReqUrl(), e.getMessage(), e);
            throw e;
        } finally {
            // 上报指标
            if (metricsClient != null) {
                long duration = System.currentTimeMillis() - beginTime;
                metricsClient.recordRpcMetric("llm_req", request.getCaller(), "llm", duration, 
                    error == null ? 0 : 1);
            }
        }
    }
    
    /**
     * 使用 LangChain4j 调用 LLM（新实现）
     */
    private LLMResponse requestLLMWithLangChain4j(RequestLLMRequest request, long beginTime) throws Exception {
        try {
            // 使用 LangChain4j 调用
            String content = langChain4jLLMService.generate(
                request.getCaller(), 
                request.getPrompt(), 
                request.getReqUrl()
            );
            
            // 构建响应对象（保持原有格式）
            LLMResponse llmResponse = new LLMResponse();
            llmResponse.setId("langchain4j-" + System.currentTimeMillis());
            llmResponse.setObject("chat.completion");
            llmResponse.setCreated(System.currentTimeMillis() / 1000);
            llmResponse.setModel("langchain4j");
            
            LLMResponse.LLMResponseChoice choice = new LLMResponse.LLMResponseChoice();
            choice.setIndex(0);
            LLMResponse.LLMResponseMessage message = new LLMResponse.LLMResponseMessage();
            message.setRole("assistant");
            message.setContent(content);
            choice.setMessage(message);
            choice.setFinishReason("stop");
            
            llmResponse.setChoices(List.of(choice));
            
            LLMResponse.LLMResponseUsage usage = new LLMResponse.LLMResponseUsage();
            usage.setPromptTokens(0); // LangChain4j 可能不提供，需要从响应中提取
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            llmResponse.setUsage(usage);
            
            log.info("requestLLM (LangChain4j) pic_url: {}, cost: {}ms", 
                request.getReqUrl(), System.currentTimeMillis() - beginTime);
            
            return llmResponse;
            
        } catch (Exception e) {
            log.error("LangChain4j 调用失败，回退到原有实现: error={}", e.getMessage(), e);
            // 如果 LangChain4j 调用失败，回退到原有实现
            return requestLLMLegacy(request, beginTime);
        }
    }
    
    /**
     * 原有实现（保留作为回退方案）
     */
    private LLMResponse requestLLMLegacy(RequestLLMRequest request, long beginTime) throws Exception {
            // 构建消息
            LLMMessage message = new LLMMessage();
            message.setRole("user");
            
            List<LLMMessageContent> contents = new ArrayList<>();
            // 文本内容
            LLMMessageContent textContent = new LLMMessageContent();
            textContent.setType("text");
            textContent.setText(request.getPrompt());
            contents.add(textContent);
            
            // 图片内容
            LLMMessageContent imageContent = new LLMMessageContent();
            imageContent.setType("image_url");
            MessageImageUrl imageUrl = new MessageImageUrl();
            imageUrl.setUrl(request.getReqUrl());
            imageContent.setImageUrl(imageUrl);
            contents.add(imageContent);
            
            message.setContent(contents);
            
            // 构建请求体
            LLMRequest llmReq = objectMapper.readValue(request.getParams(), LLMRequest.class);
            llmReq.setMessages(List.of(message));
            
            // 获取HTTP端点
            String endpoint = getHttpEndpoint(request.getLlmServiceName());
            if (endpoint == null || endpoint.isEmpty()) {
                throw new Exception("no valid llm endpoint: " + request.getLlmServiceName());
            }
            
            String url = "http://" + endpoint + "/v1/chat/completions";
            
            // 发送HTTP请求
            String requestBody = objectMapper.writeValueAsString(llmReq);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .headers(request.getHeaders().entrySet().stream()
                            .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                            .toArray(String[]::new))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new Exception("HTTP请求失败: statusCode=" + response.statusCode() + ", body=" + response.body());
            }
            
            // 解析响应
            LLMResponse llmResponse = objectMapper.readValue(response.body(), LLMResponse.class);
            
        log.info("requestLLM (Legacy) pic_url: {}, cost: {}ms", 
            request.getReqUrl(), System.currentTimeMillis() - beginTime);
            
            return llmResponse;
    }
    
    /**
     * 获取LLM集群配置
     */
    private LLMClusterConf getLLMClusterConf(String businessName) {
        LLMClusterConf defaultConf = getDefaultLLMClusterConf();
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.error("获取配置失败: {}, 使用默认配置", AppConfigService.OCR_LLM_CONF);
            return defaultConf;
        }
        
        // TODO: 实现WithEnvPrefix逻辑（根据环境添加test_前缀）
        String confKey = "llm_cluster_conf_" + businessName;
        String confStr = params.get(confKey);
        
        if (confStr == null || confStr.isEmpty()) {
            return defaultConf;
        }
        
        try {
            LLMClusterConf conf = objectMapper.readValue(confStr, LLMClusterConf.class);
            return conf;
        } catch (Exception e) {
            log.error("解析LLM集群配置失败: businessName={}, conf={}, error={}", 
                businessName, confStr, e.getMessage(), e);
            return defaultConf;
        }
    }
    
    /**
     * 获取默认LLM集群配置
     */
    private LLMClusterConf getDefaultLLMClusterConf() {
        LLMClusterConf conf = new LLMClusterConf();
        conf.setServiceName("disf!machinelearning-luban-online-online-service-biz-Nautilus-OCR_model_online");
        conf.setAppId("k8s-sv0-uozuez-1754050816242");
        
        LLMClusterConf.LLMParams params = new LLMClusterConf.LLMParams();
        params.setModel("/nfs/ofs-llm-ssd/Qwen2.5-VL-32B-Instruct/main");
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
     * 获取HTTP端点
     * 
     * @param serviceName 服务名称（支持服务发现格式，如 "disf!service-name" 或直接 IP:Port）
     * @return HTTP端点（格式：ip:port），如果获取失败返回null
     */
    private String getHttpEndpoint(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("服务名称为空");
            return null;
        }
        
        // 兼容测试环境的VIP（不包含disf!前缀，直接是IP:Port格式）
        if (!serviceName.contains("disf!") && serviceName.contains(":")) {
            log.debug("使用测试环境VIP: {}", serviceName);
            return serviceName;
        }
        
        // 使用服务发现获取服务端点
        if (serviceDiscovery != null && serviceDiscovery.isAvailable()) {
            String endpoint = serviceDiscovery.getHttpEndpoint(serviceName);
            if (endpoint != null && !endpoint.isEmpty()) {
                return endpoint;
            }
        }
        
        // 如果服务发现不可用，记录警告
        log.warn("服务发现不可用，无法获取端点: {}", serviceName);
        log.warn("请确保 Nacos 或其他服务发现组件已正确配置和初始化");
        
        return null;
    }
}

