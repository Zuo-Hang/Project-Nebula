package com.wuxiansheng.shieldarch.marsdata.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.BusinessConfigService;
import com.wuxiansheng.shieldarch.marsdata.config.ExpireConfigService;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息处理器
 */
@Slf4j
@Component
public class MessageHandler {
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Autowired
    private BusinessRegistry businessRegistry;
    
    @Autowired
    private ReasonService reasonService;
    
    @Autowired
    private ExpireConfigService expireConfigService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private BusinessConfigService businessConfigService;
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * 处理消息
     * 
     * @param msg 消息内容（JSON字符串）
     * @throws Exception 处理失败时抛出异常
     */
    public void handleMsg(String msg) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 提取sourceUniqueId
            String sourceUniqueId;
            try {
                sourceUniqueId = extractSourceUniqueId(msg);
            } catch (MsgFormatException e) {
                // 消息格式错误，不再重试
                log.warn("消息格式错误: {}", e.getMessage());
                throw e;
            }
            
            if (sourceUniqueId == null || sourceUniqueId.isEmpty()) {
                log.info("消息中未找到sourceUniqueId，跳过处理");
                return;
            }
            
            // 2. 创建业务对象列表（一条消息可能对应多条链路）
            List<Business> businesses = businessRegistry.createBusinesses(sourceUniqueId, msg);
            if (businesses.isEmpty()) {
                log.info("消息不需要处理，source_unique_id: {}", sourceUniqueId);
                return;
            }
            
            // 3. 并发处理所有业务
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Business business : businesses) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        BusinessContext bctx = createBusinessContext(business, sourceUniqueId);
                        handlerBusiness(bctx, business);
                    } catch (Exception e) {
                        log.error("处理业务失败: {}", business.getName(), e);
                        throw new RuntimeException(e);
                    }
                }, executorService);
                futures.add(future);
            }
            
            // 等待所有业务处理完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // 上报指标
            if (metricsClient != null) {
                long duration = System.currentTimeMillis() - startTime;
                metricsClient.recordRpcMetric("HandlerMsg", sourceUniqueId, "all", duration, 0);
            }
            
        } catch (Exception e) {
            // 上报错误指标
            if (metricsClient != null) {
                long duration = System.currentTimeMillis() - startTime;
                metricsClient.recordRpcMetric("HandlerMsg", "unknown", "all", duration, 1);
            }
            throw e;
        }
    }
    
    /**
     * 处理单个业务
     */
    private void handlerBusiness(BusinessContext bctx, Business business) throws Exception {
        long startTime = System.currentTimeMillis();
        String businessName = business.getName();
        
        try {
            log.info("开始处理业务: {}, detail: {}", businessName, business);
            
            // 1. 过期检查
            long msgTimestamp = business.getMsgTimestamp();
            long latency = System.currentTimeMillis() / 1000 - msgTimestamp;
            
            if (metricsClient != null) {
                metricsClient.recordGauge("msg_latency_s", latency, Map.of("business", businessName));
            }
            
            if (isExpired(msgTimestamp, businessName)) {
                if (metricsClient != null) {
                    metricsClient.incrementCounter("expire_msg_counter", Map.of("business", businessName));
                }
                log.warn("收到过期消息, business: {}, timestamp: {}", businessName, msgTimestamp);
                throw new MsgExpiredException("消息已过期");
            }
            
            if (metricsClient != null) {
                metricsClient.incrementCounter("msgs_filtered", Map.of("business", businessName));
            }
            
            // 2. 执行推理
            List<ReasonRequest> reasonRequests = business.getReasonRequests();
            List<ReasonResponse> responses = reasonService.batchReason(bctx, reasonRequests, businessName);
            
            // 检查推理错误
            for (ReasonResponse response : responses) {
                if (response.hasError()) {
                    log.warn("HandlerMsg BatchReason失败, business: {}, error: {}", 
                        businessName, response.getError());
                    throw new ReasonFailException("推理失败: " + response.getError().getMessage());
                }
            }
            
            // 3. Merge 推理结果
            business.merge(responses);
            log.info("合并后的业务: {}, detail: {}", businessName, business);
            
            // 4. 执行 Posters
            List<Poster> posters = businessRegistry.getPosters(businessName);
            for (Poster poster : posters) {
                if (metricsClient != null) {
                    metricsClient.incrementCounter("poster_counter", Map.of("business", businessName));
                }
                business = poster.apply(bctx, business);
            }
            log.info("Poster处理后的业务: {}, detail: {}", businessName, business);
            
            // 5. 执行 Sinkers
            List<Sinker> sinkers = businessRegistry.getSinkers(businessName);
            for (Sinker sinker : sinkers) {
                if (metricsClient != null) {
                    metricsClient.incrementCounter("sink_counter", Map.of("business", businessName));
                }
                try {
                    sinker.sink(bctx, business);
                } catch (Exception e) {
                    if (metricsClient != null) {
                        metricsClient.incrementCounter("sink_fail", 
                            Map.of("business", businessName, "sink", sinker.getClass().getSimpleName()));
                    }
                    log.warn("sink.Sink失败, business: {}, error: {}", businessName, e.getMessage());
                    // 不抛出异常，继续执行其他sinker
                }
            }
            
            // 上报指标
            if (metricsClient != null) {
                long duration = System.currentTimeMillis() - startTime;
                metricsClient.recordRpcMetric("HandlerBusiness", "all", businessName, duration, 0);
            }
            
        } catch (Exception e) {
            // 上报错误指标
            if (metricsClient != null) {
                long duration = System.currentTimeMillis() - startTime;
                metricsClient.recordRpcMetric("HandlerBusiness", "all", businessName, duration, 1);
            }
            throw e;
        }
    }
    
    /**
     * 创建业务上下文
     */
    private BusinessContext createBusinessContext(Business business, String sourceUniqueId) {
        String businessName = business.getName();
        BusinessContext.BusinessConf businessConf = null;
        BusinessContext.SourceConf sourceConf = null;
        
        if (businessConfigService != null) {
            BusinessConfigService.BusinessConf config = businessConfigService.getBusinessConf(businessName);
            if (config != null) {
                // 转换为BusinessContext中的类型
                businessConf = new BusinessContext.BusinessConf();
                businessConf.setName(config.getName());
                businessConf.setEnable(config.isEnable());
                businessConf.setMaxConcurrent(config.getMaxConcurrent());
                
                // 转换sources
                if (config.getSources() != null) {
                    List<BusinessContext.SourceConf> sources = new ArrayList<>();
                    for (BusinessConfigService.BusinessSourceConf src : config.getSources()) {
                        BusinessContext.SourceConf sc = new BusinessContext.SourceConf();
                        sc.setUniqueId(src.getUniqueId());
                        sc.setTest(src.isTest());
                        sc.setLevel(src.getLevel());
                        sources.add(sc);
                    }
                    businessConf.setSources(sources);
                }
                
                // 获取sourceConf
                BusinessConfigService.BusinessSourceConf srcConf = config.getSourceConf(sourceUniqueId);
                if (srcConf != null) {
                    sourceConf = new BusinessContext.SourceConf();
                    sourceConf.setUniqueId(srcConf.getUniqueId());
                    sourceConf.setTest(srcConf.isTest());
                    sourceConf.setLevel(srcConf.getLevel());
                }
            }
        }
        
        BusinessContext bctx = new BusinessContext();
        bctx.setBusinessConf(businessConf);
        bctx.setSourceConf(sourceConf);
        
        return bctx;
    }
    
    /**
     * 检查消息是否过期
     */
    private boolean isExpired(long msgTimestamp, String businessName) {
        if (expireConfigService == null) {
            // 如果没有配置服务，使用默认阈值
            long currentTime = System.currentTimeMillis() / 1000;
            return currentTime - msgTimestamp > DEFAULT_EXPIRE_THRESHOLD;
        }
        
        // 如果配置为使用过期数据，则不过期
        if (expireConfigService.isUseExpireData(businessName)) {
            return false;
        }
        
        // 获取过期阈值
        long expireThreshold = expireConfigService.getExpireDataThreshold(businessName);
        long currentTime = System.currentTimeMillis() / 1000;
        
        return currentTime - msgTimestamp > expireThreshold;
    }
    
    /**
     * 默认过期阈值（秒）：48小时
     */
    private static final long DEFAULT_EXPIRE_THRESHOLD = 86400L * 2;
    
    /**
     * 从消息中提取sourceUniqueId
     * 
     * @param msg 消息内容
     * @return sourceUniqueId
     * @throws MsgFormatException 消息格式错误
     */
    private String extractSourceUniqueId(String msg) throws MsgFormatException {
        try {
            // 尝试解析为问卷消息
            @SuppressWarnings("unchecked")
            Map<String, Object> questMsg = (Map<String, Object>) objectMapper.readValue(msg, Map.class);
            Object activityName = questMsg.get("activityName");
            if (activityName != null && !activityName.toString().isEmpty()) {
                return activityName.toString();
            }
            
            // 尝试解析为自定义业务消息
            Object business = questMsg.get("business");
            if (business != null && !business.toString().isEmpty()) {
                return business.toString();
            }
            
        } catch (Exception e) {
            log.warn("extractSourceUniqueId解析消息失败: {}, error: {}", msg, e.getMessage());
            throw new MsgFormatException("消息格式错误，无法提取sourceUniqueId: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 消息过期异常
     */
    public static class MsgExpiredException extends Exception {
        public MsgExpiredException(String message) {
            super(message);
        }
    }
    
    /**
     * 消息格式错误异常
     */
    public static class MsgFormatException extends Exception {
        public MsgFormatException(String message) {
            super(message);
        }
    }
    
    /**
     * 推理失败异常
     */
    public static class ReasonFailException extends Exception {
        public ReasonFailException(String message) {
            super(message);
        }
        
        public ReasonFailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

