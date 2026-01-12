package com.wuxiansheng.shieldarch.marsdata.llm.sinker;

import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 延迟监控Sinker
 */
@Slf4j
@Component
public class LatencySinker implements Sinker {
    
    private static final String DEFAULT_METRIC_NAME = "sinker_monitor_latency";
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (business == null) {
            return;
        }
        
        long msgTs = business.getMsgTimestamp();
        if (msgTs <= 0) {
            return;
        }
        
        long now = System.currentTimeMillis() / 1000;
        long latency = Math.max(0, now - msgTs);
        
        if (metricsClient != null) {
            metricsClient.recordRpcMetric(DEFAULT_METRIC_NAME, "all", business.getName(), 
                latency * 1000, 0); // 转换为毫秒
        }
    }
}

