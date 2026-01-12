package com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusiness;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 监控Sinker
 */
@Slf4j
@Component
public class MonitorSinker implements Sinker {
    
    private static final String MONITOR_METRIC = "b_saas_monitor";
    private static final String MONITOR_COUNT_METRIC = "b_saas_monitor_count";
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof BSaasBusiness)) {
            return;
        }
        
        BSaasBusiness bs = (BSaasBusiness) business;
        if (bs.getReasonResult() == null || metricsClient == null) {
            return;
        }
        
        reportOrderMetrics(bs);
        reportDetailMetrics(bs);
        reportPerformanceMetrics(bs);
        reportVerifyMetrics(bs);
    }
    
    /**
     * 上报订单指标
     */
    private void reportOrderMetrics(BSaasBusiness bs) {
        String supplier = bs.getInput() != null && bs.getInput().getMeta() != null ? 
            bs.getInput().getMeta().getSupplierName() : "";
        String city = bs.getInput() != null && bs.getInput().getMeta() != null ? 
            bs.getInput().getMeta().getCityName() : "";
        
        metricsClient.incrementCounter(MONITOR_COUNT_METRIC, 
            Map.of("type", "order", "supplier", supplier, "city", city));
        
        if (bs.getReasonResult().getOrderList() == null || 
            bs.getReasonResult().getOrderList().isEmpty()) {
            metricsClient.incrementCounter(MONITOR_METRIC,
                Map.of("field", "order_count", "type", "empty"));
        }
        
        if (bs.getReasonResult().getFilteredOrders() != null && 
            !bs.getReasonResult().getFilteredOrders().isEmpty()) {
            metricsClient.incrementCounter(MONITOR_METRIC,
                Map.of("field", "order_filtered", "type", "exist"));
        }
    }
    
    /**
     * 上报明细指标
     */
    private void reportDetailMetrics(BSaasBusiness bs) {
        String supplier = bs.getInput() != null && bs.getInput().getMeta() != null ? 
            bs.getInput().getMeta().getSupplierName() : "";
        String city = bs.getInput() != null && bs.getInput().getMeta() != null ? 
            bs.getInput().getMeta().getCityName() : "";
        
        metricsClient.incrementCounter(MONITOR_COUNT_METRIC,
            Map.of("type", "detail", "supplier", supplier, "city", city));
        
        boolean hasPassenger = bs.getReasonResult().getPassengerDetails() != null && 
            !bs.getReasonResult().getPassengerDetails().isEmpty();
        boolean hasDriver = bs.getReasonResult().getDriverDetails() != null && 
            !bs.getReasonResult().getDriverDetails().isEmpty();
        
        if (!hasPassenger && !hasDriver) {
            metricsClient.incrementCounter(MONITOR_METRIC,
                Map.of("field", "detail_count", "type", "empty"));
        }
    }
    
    /**
     * 上报性能指标
     */
    private void reportPerformanceMetrics(BSaasBusiness bs) {
        if (bs.getReasonResult().getMergedStats() == null || 
            bs.getReasonResult().getMergedStats().isEmpty()) {
            metricsClient.incrementCounter(MONITOR_METRIC,
                Map.of("field", "merged_stats", "type", "empty"));
        }
    }
    
    /**
     * 上报校验指标
     */
    private void reportVerifyMetrics(BSaasBusiness bs) {
        if (bs.getReasonResult().getVerifyRecords() == null || 
            bs.getReasonResult().getVerifyRecords().isEmpty()) {
            return;
        }
        metricsClient.incrementCounter(MONITOR_METRIC,
            Map.of("field", "verify_records", "type", "exist"));
    }
}

