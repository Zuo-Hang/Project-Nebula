package com.wuxiansheng.shieldarch.marsdata.business.xlbubble.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleReasonResult;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 小拉冒泡监控Sinker
 */
@Slf4j
@Component
public class XLSinkerMonitor implements Sinker {
    
    private static final String MONITOR_METRICS = "sinker_monitor";
    private static final String MONITOR_COUNT_METRICS = "sinker_monitor_count";
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof XLBubbleBusiness)) {
            return;
        }
        
        XLBubbleBusiness gb = (XLBubbleBusiness) business;
        String businessName = business.getName();
        
        if (gb.getReasonResult() == null) {
            return;
        }
        
        XLBubbleReasonResult reasonResult = gb.getReasonResult();
        
        // 监控空值
        monitorFloatEmpty(businessName, reasonResult.getEstimatedDistance(), "estimate_distance");
        monitorFloatEmpty(businessName, reasonResult.getEstimatedTime(), "estimate_time");
        monitorStringEmpty(businessName, reasonResult.getStartPoint(), "start_point");
        monitorStringEmpty(businessName, reasonResult.getEndPoint(), "end_point");
        monitorStringEmpty(businessName, reasonResult.getBubbleTime(), "create_time");
        
        // 监控供应商
        if (reasonResult.getSuppliersInfo() != null) {
            for (ReasonSupplierResult supplierInfo : reasonResult.getSuppliersInfo()) {
                if (metricsClient != null) {
                    metricsClient.incrementCounter(MONITOR_COUNT_METRICS, 
                        Map.of("supplier", supplierInfo.getSupplier()));
                }
                monitorFloatEmpty(businessName, supplierInfo.getPrice(), "price");
            }
        }
    }
    
    /**
     * 监控浮点数为空
     */
    private void monitorFloatEmpty(String businessName, Double val, String field) {
        if (val == null || val == 0.0) {
            if (metricsClient != null) {
                metricsClient.incrementCounter(MONITOR_METRICS, 
                    Map.of("business", businessName, "field", field, "type", "empty"));
            }
        }
    }
    
    /**
     * 监控字符串为空
     */
    private void monitorStringEmpty(String businessName, String val, String field) {
        if (val == null || val.isEmpty()) {
            if (metricsClient != null) {
                metricsClient.incrementCounter(MONITOR_METRICS, 
                    Map.of("business", businessName, "field", field, "type", "empty"));
            }
        }
    }
}

