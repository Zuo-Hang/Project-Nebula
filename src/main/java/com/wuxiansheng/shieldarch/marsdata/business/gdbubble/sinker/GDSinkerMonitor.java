package com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleReasonResult;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 高德冒泡监控Sinker
 */
@Slf4j
@Component
public class GDSinkerMonitor implements Sinker {
    
    private static final String MONITOR_METRICS = "sinker_monitor";
    private static final String MONITOR_COUNT_METRICS = "sinker_monitor_count";
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof GDBubbleBusiness)) {
            return;
        }
        
        GDBubbleBusiness gb = (GDBubbleBusiness) business;
        
        if (gb.getReasonResult() == null) {
            return;
        }
        
        GDBubbleReasonResult reasonResult = gb.getReasonResult();
        
        // 监控空值
        monitorFloatEmpty(reasonResult.getEstimatedDistance(), "estimate_distance");
        monitorFloatEmpty(reasonResult.getEstimatedTime(), "estimate_time");
        monitorStringEmpty(reasonResult.getStartPoint(), "start_point");
        monitorStringEmpty(reasonResult.getEndPoint(), "end_point");
        monitorStringEmpty(reasonResult.getCreationTime(), "create_time");
        
        // 监控供应商
        if (reasonResult.getSuppliersInfo() != null) {
            for (ReasonSupplierResult supplierInfo : reasonResult.getSuppliersInfo()) {
                if (metricsClient != null) {
                    metricsClient.incrementCounter(MONITOR_COUNT_METRICS, 
                        Map.of("supplier", supplierInfo.getSupplier()));
                }
                
                // 检查价格是否为空
                if ((supplierInfo.getEstPrice() == null || supplierInfo.getEstPrice() == 0.0) &&
                    (supplierInfo.getCapPrice() == null || supplierInfo.getCapPrice() == 0.0) &&
                    (supplierInfo.getPriceRange() == null || supplierInfo.getPriceRange().isEmpty())) {
                    monitorFloatEmpty(0.0, "price");
                }
            }
        }
    }
    
    /**
     * 监控浮点数为空
     */
    private void monitorFloatEmpty(Double val, String field) {
        if (val == null || val == 0.0) {
            if (metricsClient != null) {
                metricsClient.incrementCounter(MONITOR_METRICS, 
                    Map.of("field", field, "type", "empty"));
            }
        }
    }
    
    /**
     * 监控字符串为空
     */
    private void monitorStringEmpty(String val, String field) {
        if (val == null || val.isEmpty()) {
            if (metricsClient != null) {
                metricsClient.incrementCounter(MONITOR_METRICS, 
                    Map.of("field", field, "type", "empty"));
            }
        }
    }
}

