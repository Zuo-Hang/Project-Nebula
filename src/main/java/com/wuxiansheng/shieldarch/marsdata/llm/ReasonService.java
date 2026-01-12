package com.wuxiansheng.shieldarch.marsdata.llm;

import com.wuxiansheng.shieldarch.marsdata.config.AppConfigService;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 推理服务
 */
@Slf4j
@Service
public class ReasonService {
    
    @Autowired
    private LLMCacheService llmCacheService;
    
    @Autowired
    private LLMClient llmClient;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Autowired
    private AppConfigService appConfigService;
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * 当前使用的token数量（用于并发控制）
     */
    private final AtomicInteger usedTokens = new AtomicInteger(0);
    
    /**
     * 批量推理
     * 
     * @param bctx 业务上下文
     * @param requests 推理请求列表
     * @param businessName 业务名称
     * @return 推理响应列表
     */
    public List<ReasonResponse> batchReason(BusinessContext bctx, List<ReasonRequest> requests, String businessName) {
        if (requests == null || requests.isEmpty()) {
            log.info("[LLM推理] 业务 {} 没有LLM请求，跳过推理", businessName);
            return new ArrayList<>();
        }
        
        log.info("batch reason req, business: {}, len(req): {}", businessName, requests.size());
        
        // 先检查缓存，分离缓存命中和未命中的请求
        List<ReasonResponse> responses = new ArrayList<>(requests.size());
        List<Integer> uncachedIndices = new ArrayList<>();
        List<ReasonRequest> uncachedRequests = new ArrayList<>();
        
        for (int i = 0; i < requests.size(); i++) {
            ReasonRequest request = requests.get(i);
            ReasonResponse cachedResponse = tryGetFromCache(request, businessName);
            if (cachedResponse != null) {
                responses.add(cachedResponse);
            } else {
                // 占位，后续填充
                responses.add(null);
                uncachedIndices.add(i);
                uncachedRequests.add(request);
            }
        }
        
        // 全部命中缓存
        if (uncachedRequests.isEmpty()) {
            return responses;
        }
        
        log.info("batch request llm, business: {}, size: {}", businessName, uncachedRequests.size());
        
        // 分批次并发推理
        List<ReasonResponse> uncachedResponses = reasonBySubBatch(bctx, uncachedRequests, businessName);
        
        // 合并到结果
        for (int i = 0; i < uncachedIndices.size(); i++) {
            int index = uncachedIndices.get(i);
            responses.set(index, uncachedResponses.get(i));
        }
        
        return responses;
    }
    
    /**
     * 分批推理
     */
    private List<ReasonResponse> reasonBySubBatch(BusinessContext bctx, List<ReasonRequest> requests, String businessName) {
        if (requests.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 获取最大并发数
        int maxConcurrent = 0;
        if (bctx.getBusinessConf() != null) {
            maxConcurrent = bctx.getBusinessConf().getMaxConcurrent();
        }
        
        // 分批
        List<List<ReasonRequest>> subBatches = splitRequests(requests, maxConcurrent);
        
        // 按批次并发
        List<ReasonResponse> allResponses = new ArrayList<>();
        for (List<ReasonRequest> subBatch : subBatches) {
            List<ReasonResponse> batchResponses = reasonConcurrently(bctx, subBatch, businessName);
            allResponses.addAll(batchResponses);
        }
        
        return allResponses;
    }
    
    /**
     * 分割请求列表
     */
    private List<List<ReasonRequest>> splitRequests(List<ReasonRequest> requests, int subBatchSize) {
        if (requests.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 为0时全部并发
        if (subBatchSize == 0) {
            return List.of(requests);
        }
        
        List<List<ReasonRequest>> result = new ArrayList<>();
        int batchId = 1;
        while (batchId * subBatchSize <= requests.size()) {
            int start = (batchId - 1) * subBatchSize;
            int end = batchId * subBatchSize;
            result.add(new ArrayList<>(requests.subList(start, end)));
            batchId++;
        }
        
        if ((batchId - 1) * subBatchSize < requests.size()) {
            int start = (batchId - 1) * subBatchSize;
            result.add(new ArrayList<>(requests.subList(start, requests.size())));
        }
        
        return result;
    }
    
    /**
     * 并发推理
     */
    private List<ReasonResponse> reasonConcurrently(BusinessContext bctx, List<ReasonRequest> requests, String businessName) {
        // 获取本地最大并发数
        int level = 0;
        if (bctx.getSourceConf() != null) {
            level = bctx.getSourceConf().getLevel();
        }
        int localMaxConcurrent = getLLMLocalConcurrent(level);
        
        // 申请token
        if (!applyTokens(requests.size(), localMaxConcurrent)) {
            log.error("申请token失败，超过最大并发限制: need={}, max={}", requests.size(), localMaxConcurrent);
            // 返回错误响应
            List<ReasonResponse> errorResponses = new ArrayList<>();
            for (ReasonRequest request : requests) {
                ReasonResponse response = new ReasonResponse();
                response.setContext(request.getContext());
                response.setError(new Exception("rate limiter: exceeded max concurrent"));
                errorResponses.add(response);
            }
            return errorResponses;
        }
        
        try {
            // 并发推理
            List<CompletableFuture<ReasonResponse>> futures = new ArrayList<>();
            for (ReasonRequest request : requests) {
                CompletableFuture<ReasonResponse> future = CompletableFuture.supplyAsync(() -> {
                    return reasonWithoutCache(bctx, request, businessName);
                }, executorService);
                futures.add(future);
            }
            
            // 等待所有推理完成
            List<ReasonResponse> responses = new ArrayList<>();
            for (CompletableFuture<ReasonResponse> future : futures) {
                responses.add(future.join());
            }
            
            return responses;
        } finally {
            // 释放token
            returnTokens(requests.size());
        }
    }
    
    /**
     * 申请token（并发控制）
     */
    private boolean applyTokens(int needTokens, int maxConcurrent) {
        if (needTokens <= 0) {
            return true;
        }
        
        while (true) {
            int currentUsed = usedTokens.get();
            if (maxConcurrent - currentUsed < needTokens) {
                return false;
            }
            int newUsed = currentUsed + needTokens;
            if (usedTokens.compareAndSet(currentUsed, newUsed)) {
                break;
            }
        }
        return true;
    }
    
    /**
     * 释放token
     */
    private void returnTokens(int tokens) {
        while (true) {
            int currentUsed = usedTokens.get();
            int newUsed = currentUsed - tokens;
            if (usedTokens.compareAndSet(currentUsed, newUsed)) {
                break;
            }
        }
    }
    
    /**
     * 获取本地最大并发数
     */
    private int getLLMLocalConcurrent(int level) {
        int[] defaultConcurrents = {150, 70, 50};
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            return defaultConcurrents[0];
        }
        
        String key = "llm_local_concurrent_" + level;
        String valStr = params.get(key);
        if (valStr != null && !valStr.isEmpty()) {
            try {
                return Integer.parseInt(valStr);
            } catch (NumberFormatException e) {
                log.warn("解析llm_local_concurrent失败: key={}, value={}", key, valStr);
            }
        }
        
        return defaultConcurrents[Math.abs(level) % 3];
    }
    
    /**
     * 尝试从缓存获取LLM结果
     */
    private ReasonResponse tryGetFromCache(ReasonRequest request, String businessName) {
        // 如果request为null，不进行缓存查询
        if (request == null) {
            return null;
        }
        
        // 如果没有图片URL，不进行缓存查询
        if (request.getPicUrl() == null || request.getPicUrl().isEmpty()) {
            return null;
        }
        
        try {
            LLMCacheService.LLMCacheResult cacheResult = llmCacheService.getLLMCache(
                request.getPicUrl(), businessName, request.getPrompt());
            
            if (cacheResult == null) {
                logCacheEvent("miss", request.getPicUrl(), businessName, null);
                return null;
            }
            
            // 检查缓存是否有效
            if (!llmCacheService.isLLMCacheValid(cacheResult, businessName)) {
                logCacheEvent("miss", request.getPicUrl(), businessName, null);
                return null;
            }
            
            logCacheEvent("hit", request.getPicUrl(), businessName, null);
            
            ReasonResponse response = new ReasonResponse();
            response.setContext(request.getContext());
            response.setContent(cacheResult.getContent());
            return response;
            
        } catch (Exception e) {
            logCacheEvent("error", request.getPicUrl(), businessName, e);
            return null;
        }
    }
    
    /**
     * 记录缓存事件
     */
    private void logCacheEvent(String eventType, String picUrl, String businessName, Exception error) {
        switch (eventType) {
            case "error":
                log.warn("LLM cache error for image: {}, business: {}, error: {}", picUrl, businessName, error);
                if (metricsClient != null) {
                    metricsClient.incrementCounter("llm_cache_error", Map.of("business", businessName));
                }
                break;
            case "miss":
                log.info("LLM cache miss for image: {}, business: {}", picUrl, businessName);
                if (metricsClient != null) {
                    metricsClient.incrementCounter("llm_cache_miss", Map.of("business", businessName));
                }
                break;
            case "hit":
                log.info("LLM cache hit for image: {}, business: {}", picUrl, businessName);
                if (metricsClient != null) {
                    metricsClient.incrementCounter("llm_cache_hit", Map.of("business", businessName));
                }
                break;
        }
    }
    
    /**
     * 无缓存推理（直接调用LLM）
     */
    private ReasonResponse reasonWithoutCache(BusinessContext bctx, ReasonRequest request, String businessName) {
        ReasonResponse response = new ReasonResponse();
        response.setContext(request.getContext());
        
        try {
            // 调用LLM进行推理
            LLMClient.RequestLLMRequest llmRequest = llmClient.newRequestLLMRequest(
                businessName, request.getPicUrl(), request.getPrompt());
            LLMClient.LLMResponse llmResponse = llmClient.requestLLM(llmRequest);
            
            log.info("business: {}, reason req_pic: {}, result: {}", 
                businessName, request.getPicUrl(), llmResponse);
            
            if (llmResponse.getChoices() == null || llmResponse.getChoices().isEmpty()) {
                log.warn("business: {}, empty choices, res: {}", businessName, llmResponse);
                response.setError(new Exception("empty choices"));
                return response;
            }
            
            String resContent = llmResponse.getChoices().get(0).getMessage().getContent();
            // 去除可能的json代码块标记
            if (resContent.startsWith("```json")) {
                resContent = resContent.substring(7);
            }
            if (resContent.endsWith("```")) {
                resContent = resContent.substring(0, resContent.length() - 3);
            }
            resContent = resContent.trim();
            
            // 将结果缓存
            cacheResult(request, businessName, resContent);
            
            response.setContent(resContent);
            return response;
            
        } catch (Exception e) {
            log.error("调用LLM失败: business={}, picUrl={}, error={}", 
                businessName, request.getPicUrl(), e.getMessage(), e);
            response.setError(e);
            return response;
        }
    }
    
    /**
     * 缓存LLM结果
     */
    private void cacheResult(ReasonRequest request, String businessName, String content) {
        // 如果request为null、没有图片URL或内容为空，不进行缓存
        if (request == null || request.getPicUrl() == null || request.getPicUrl().isEmpty() 
            || content == null || content.isEmpty()) {
            return;
        }
        
        boolean success = llmCacheService.setLLMCache(
            request.getPicUrl(), businessName, request.getPrompt(), content);
        
        if (success) {
            log.info("LLM result cached for image: {}, business: {}", request.getPicUrl(), businessName);
        } else {
            log.warn("Failed to cache LLM result for image: {}, business: {}", 
                request.getPicUrl(), businessName);
        }
    }
}

