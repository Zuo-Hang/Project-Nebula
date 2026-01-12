package com.wuxiansheng.shieldarch.marsdata.business.bsaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.*;
import com.wuxiansheng.shieldarch.marsdata.llm.classify.SimpleClassifier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B SaaS业务
 */
@Slf4j
@Data
@Component
public class BSaasBusiness implements Business, ClassifyProvider {
    
    /**
     * 输入数据
     */
    private BSaasInput input;
    
    /**
     * 推理结果
     */
    private BSaasReasonResult reasonResult;
    
    @Autowired(required = false)
    private LLMConfigHelper llmConfigHelper;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    @Override
    public String getName() {
        return "b_saas";
    }
    
    @Override
    public long getMsgTimestamp() {
        return input != null && input.getSubmitDateTime() != null 
            ? input.getSubmitDateTime().toEpochSecond(java.time.ZoneOffset.UTC) 
            : 0;
    }
    
    @Override
    public List<ReasonRequest> getReasonRequests() {
        if (input == null || input.getImages() == null) {
            return new ArrayList<>();
        }
        
        List<ReasonRequest> requests = new ArrayList<>();
        String businessName = getName();
        
        for (BSaasInput.Image image : input.getImages()) {
            if (image.getTypes() == null || image.getTypes().isEmpty()) {
                continue;
            }
            
            for (String classify : image.getTypes()) {
                String prompt = getDefaultPrompt(classify);
                if (llmConfigHelper != null) {
                    String configPrompt = llmConfigHelper.getLLMPromptWithClassify(
                        businessName, classify, prompt);
                    if (configPrompt != null && !configPrompt.isEmpty()) {
                        prompt = configPrompt;
                    }
                }
                
                if (prompt == null || prompt.isEmpty()) {
                    log.warn("GetLLMPromptWithClassify err, prompt is empty, classify: {}", classify);
                    continue;
                }
                
                ReasonRequest request = new ReasonRequest();
                request.setPicUrl(image.getUrl());
                request.setPrompt(prompt);
                
                // 设置Context
                ReasonRequest.ReasonContext context = new ReasonRequest.ReasonContext();
                Map<String, Object> customMap = new HashMap<>();
                customMap.put("category", classify);
                customMap.put("image_url", image.getUrl());
                customMap.put("image_index", image.getIndex());
                context.setCustomMap(customMap);
                request.setContext(context);
                
                requests.add(request);
            }
        }
        
        return requests;
    }
    
    @Override
    public void merge(List<ReasonResponse> results) throws Exception {
        this.reasonResult = new BSaasReasonResult();
        
        if (results == null || results.isEmpty()) {
            return;
        }
        
        for (ReasonResponse res : results) {
            if (res == null) {
                continue;
            }
            if (res.hasError()) {
                log.warn("Merge skip reason result err: {}", res.getError().getMessage());
                continue;
            }
            if (res.getContent() == null || res.getContent().isEmpty()) {
                continue;
            }
            
            String category = getReasonCategory(res.getContext());
            String imageURL = getReasonImageURL(res.getContext());
            int imageIndex = getReasonImageIndex(res.getContext());
            
            try {
                switch (category) {
                    case "order_list":
                        mergeOrderList(res.getContent(), imageURL, imageIndex);
                        break;
                    case "passenger_order_details":
                        mergePassengerDetail(res.getContent(), imageURL, imageIndex);
                        break;
                    case "driver_Income_analysis":
                        mergeDriverDetail(res.getContent(), imageURL, imageIndex);
                        break;
                    case "historical_data_statistics":
                        mergeHistoricalStatistic(res.getContent(), imageURL, imageIndex, category);
                        break;
                    case "performance_transaction_history":
                        mergePerformanceTransaction(res.getContent(), imageURL, imageIndex, category);
                        break;
                    case "personal_homepage":
                        mergePersonalHomepage(res.getContent(), imageURL, imageIndex);
                        break;
                    default:
                        log.warn("Merge skip unknown category: {}", category);
                }
            } catch (Exception e) {
                log.error("Merge error for category: {}, content: {}, error: {}", 
                    category, res.getContent(), e.getMessage(), e);
            }
        }
    }
    
    @Override
    public BusinessContext.Classifier getClassifier() {
        SimpleClassifier classifier = new SimpleClassifier();
        if (input != null && input.getImages() != null) {
            for (BSaasInput.Image image : input.getImages()) {
                if (image.getTypes() != null && !image.getTypes().isEmpty()) {
                    classifier.append(image.getUrl(), image.getTypes().toArray(new String[0]));
                }
            }
        }
        return classifier;
    }
    
    // ===================== 合并方法 =====================
    
    private void mergeOrderList(String content, String imageURL, int imageIndex) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        // 清理可能的JSON代码块标记
        content = cleanJsonContent(content);
        
        BSaasOrderListRaw[] raws = objectMapper.readValue(content, BSaasOrderListRaw[].class);
        for (int idx = 0; idx < raws.length; idx++) {
            BSaasOrderListRaw raw = raws[idx];
            int resolvedIndex = resolveImageIndex(imageIndex, idx);
            reasonResult.getOrderList().add(raw.toModel(imageURL, resolvedIndex));
        }
    }
    
    private void mergePassengerDetail(String content, String imageURL, int imageIndex) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        content = cleanJsonContent(content);
        BSaasPassengerDetailRaw raw = objectMapper.readValue(content, BSaasPassengerDetailRaw.class);
        reasonResult.getPassengerDetails().add(raw.toModel(imageURL, imageIndex));
    }
    
    private void mergeDriverDetail(String content, String imageURL, int imageIndex) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        content = cleanJsonContent(content);
        BSaasDriverDetailRaw raw = objectMapper.readValue(content, BSaasDriverDetailRaw.class);
        reasonResult.getDriverDetails().add(raw.toModel(imageURL, imageIndex));
    }
    
    private void mergeHistoricalStatistic(String content, String imageURL, int imageIndex, String category) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        content = cleanJsonContent(content);
        BSaasHistoricalStatisticRaw raw = objectMapper.readValue(content, BSaasHistoricalStatisticRaw.class);
        reasonResult.getHistoricalStatistics().add(raw.toModel(imageURL, imageIndex, category));
    }
    
    private void mergePerformanceTransaction(String content, String imageURL, int imageIndex, String category) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        content = cleanJsonContent(content);
        BSaasPerformanceTransactionRaw raw = objectMapper.readValue(content, BSaasPerformanceTransactionRaw.class);
        reasonResult.getPerformanceTransactions().add(raw.toModel(imageURL, imageIndex, category));
    }
    
    private void mergePersonalHomepage(String content, String imageURL, int imageIndex) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        content = cleanJsonContent(content);
        BSaasPersonalHomepageRaw raw = objectMapper.readValue(content, BSaasPersonalHomepageRaw.class);
        reasonResult.getPersonalHomepage().add(raw.toModel(imageURL, imageIndex));
    }
    
    // ===================== 工具方法 =====================
    
    private String getReasonCategory(ReasonRequest.ReasonContext ctx) {
        if (ctx == null || ctx.getCustomMap() == null) {
            return "";
        }
        Object val = ctx.getCustomMap().get("category");
        return val != null ? val.toString() : "";
    }
    
    private String getReasonImageURL(ReasonRequest.ReasonContext ctx) {
        if (ctx == null || ctx.getCustomMap() == null) {
            return "";
        }
        Object val = ctx.getCustomMap().get("image_url");
        return val != null ? val.toString() : "";
    }
    
    private int getReasonImageIndex(ReasonRequest.ReasonContext ctx) {
        if (ctx == null || ctx.getCustomMap() == null) {
            return -1;
        }
        Object val = ctx.getCustomMap().get("image_index");
        if (val == null) {
            return -1;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
    
    private int resolveImageIndex(int imageIndex, int offset) {
        return imageIndex >= 0 ? imageIndex : offset;
    }
    
    private String cleanJsonContent(String content) {
        if (content == null) {
            return "";
        }
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }
    
    /**
     * 获取默认prompt
     */
    private String getDefaultPrompt(String category) {
        // 由于prompt非常长，这里只返回空字符串，实际应该从配置中心获取
        return "";
    }
}

