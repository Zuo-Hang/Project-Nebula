package com.wuxiansheng.shieldarch.marsdata.llm.sinker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Hive Sinker基类
 */
@Slf4j
@Component
public class HiveSinker implements Sinker {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        // 子类实现具体逻辑
    }
    
    /**
     * 打印到Hive
     */
    protected void printToHive(Object raw, String businessName, long msgTs) {
        try {
            String params = objectMapper.writeValueAsString(raw);
            
            if (metricsClient != null) {
                metricsClient.incrementCounter("write_public", Map.of("business", businessName));
            }
            
            // timestamp：数据采集依赖时间戳，决定了采集到哪个ods层的分区。为了确保数据被采集到，使用处理时间。
            // realtime：与timestamp联合，一个代表事件时间，一个代表处理时间。
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logMessage = String.format("ocr_llm_result_v2||timestamp=%s||realtime=%d||business_name=%s||params=%s",
                timestamp, msgTs, businessName, params);
            
            // 使用logger.Public输出（在Java中，可以使用专门的日志输出或发送到MQ）
            log.info("[HIVE] {}", logMessage);
            
        } catch (Exception e) {
            log.warn("sinker.PrintToHive err: {}, raw: {}", e.getMessage(), raw, e);
        }
    }
}

