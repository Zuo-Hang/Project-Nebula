package com.wuxiansheng.shieldarch.marsdata.llm.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.AppConfigService;
import com.wuxiansheng.shieldarch.marsdata.llm.LLMClient;
import com.wuxiansheng.shieldarch.marsdata.utils.ServiceDiscovery;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用 LangChain4j 原生 ImageContent 的 LLM 服务
 * 
 * 这是升级版本，展示如何使用原生的 ImageContent 和 TextContent。
 * 
 * 使用方式对比：
 * 
 * 旧方式（ThreadLocal）：
 * ```java
 * model.setImageUrl(imageUrl);
 * UserMessage userMessage = UserMessage.userMessage(prompt);
 * Response<AiMessage> response = model.generate(List.of(userMessage));
 * model.clearContext();
 * ```
 * 
 * 新方式（原生 ImageContent）：
 * ```java
 * List<Content> contents = new ArrayList<>();
 * contents.add(TextContent.from(prompt));
 * contents.add(ImageContent.from(imageUrl));
 * UserMessage userMessage = UserMessage.userMessage(contents);
 * Response<AiMessage> response = model.generate(List.of(userMessage));
 * ```
 */
@Slf4j
@Service
public class LangChain4jLLMServiceNative {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AppConfigService appConfigService;
    
    @Autowired(required = false)
    private ServiceDiscovery serviceDiscovery;
    
    /**
     * ChatModel 缓存（按业务名称缓存）
     */
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    /**
     * 使用 LangChain4j 原生方式调用 LLM
     * 
     * @param businessName 业务名称
     * @param prompt 提示词
     * @param imageUrl 图片 URL
     * @return LLM 响应内容
     */
    public String generate(String businessName, String prompt, String imageUrl) throws Exception {
        // 1. 获取或创建 ChatModel
        ChatLanguageModel model = getOrCreateChatModel(businessName);
        
        // 2. 构建内容列表（使用原生 ImageContent）
        List<Content> contents = new ArrayList<>();
        
        // 添加文本内容
        contents.add(TextContent.from(prompt));
        
        // 添加图片内容（如果提供）
        if (imageUrl != null && !imageUrl.isEmpty()) {
            contents.add(ImageContent.from(imageUrl));
        }
        
        // 3. 创建多模态消息（图片 + 文本）
        UserMessage userMessage = UserMessage.userMessage(contents);
        
        // 4. 调用 LLM
        Response<AiMessage> response = model.generate(List.of(userMessage));
        
        // 5. 提取内容
        String content = response.content().text();
        
        log.debug("LangChain4j (Native) LLM 调用成功: business={}, contentLength={}", 
            businessName, content != null ? content.length() : 0);
        
        return content;
    }
    
    /**
     * 使用 Base64 图片调用 LLM
     * 
     * @param businessName 业务名称
     * @param prompt 提示词
     * @param base64Image Base64 编码的图片
     * @param mimeType 图片 MIME 类型（如 "image/jpeg"）
     * @return LLM 响应内容
     */
    public String generateWithBase64Image(String businessName, String prompt, 
                                         String base64Image, String mimeType) throws Exception {
        ChatLanguageModel model = getOrCreateChatModel(businessName);
        
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(prompt));
        
        if (base64Image != null && !base64Image.isEmpty()) {
            contents.add(ImageContent.from(base64Image, mimeType));
        }
        
        UserMessage userMessage = UserMessage.userMessage(contents);
        Response<AiMessage> response = model.generate(List.of(userMessage));
        
        return response.content().text();
    }
    
    /**
     * 获取或创建 ChatModel
     */
    private ChatLanguageModel getOrCreateChatModel(String businessName) {
        return modelCache.computeIfAbsent(businessName, name -> {
            // 获取 LLM 集群配置
            LLMClient.LLMClusterConf conf = getLLMClusterConf(name);
            
            // 创建 ChatModel（使用原生版本）
            return new DiSFChatModelNative(
                conf.getServiceName(),
                conf.getAppId(),
                conf.getParams().getModel(),
                conf.getParams().getMaxTokens(),
                conf.getParams().getTemperature(),
                conf.getParams().getTopK(),
                conf.getParams().getTopP(),
                conf.getParams().getRepetitionPenalty(),
                conf.getParams().isStream(),
                serviceDiscovery,
                objectMapper
            );
        });
    }
    
    /**
     * 获取 LLM 集群配置（复用现有逻辑）
     */
    private LLMClient.LLMClusterConf getLLMClusterConf(String businessName) {
        LLMClient.LLMClusterConf defaultConf = getDefaultLLMClusterConf();
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.error("获取配置失败: {}, 使用默认配置", AppConfigService.OCR_LLM_CONF);
            return defaultConf;
        }
        
        String confKey = "llm_cluster_conf_" + businessName;
        String confStr = params.get(confKey);
        
        if (confStr == null || confStr.isEmpty()) {
            return defaultConf;
        }
        
        try {
            LLMClient.LLMClusterConf conf = objectMapper.readValue(confStr, LLMClient.LLMClusterConf.class);
            return conf;
        } catch (Exception e) {
            log.error("解析 LLM 集群配置失败: businessName={}, conf={}, error={}", 
                businessName, confStr, e.getMessage(), e);
            return defaultConf;
        }
    }
    
    /**
     * 获取默认 LLM 集群配置（复用现有逻辑）
     */
    private LLMClient.LLMClusterConf getDefaultLLMClusterConf() {
        LLMClient.LLMClusterConf conf = new LLMClient.LLMClusterConf();
        conf.setServiceName("disf!machinelearning-luban-online-online-service-biz-Nautilus-OCR_model_online");
        conf.setAppId("k8s-sv0-uozuez-1754050816242");
        
        LLMClient.LLMClusterConf.LLMParams params = new LLMClient.LLMClusterConf.LLMParams();
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
}

