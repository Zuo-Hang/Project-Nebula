package com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.GDSpecialPriceBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 高德特价车监控Sinker
 */
@Slf4j
@Component
public class GDSpecialPriceSinkerMonitor implements Sinker {
    
    private static final String RECOGNITION_SUCCESS_METRIC = "sinker_recognition_success_count";
    private static final String CAP_PRICE_OUT_OF_RANGE_METRIC = "sinker_cap_price_out_of_range";
    private static final String MOCK_PARTNER_METRIC = "sinker_mock_partner_count";
    
    private static final double MIN_CAP_PRICE = 1.0;
    private static final double MAX_CAP_PRICE = 120.0;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof GDSpecialPriceBusiness)) {
            return;
        }
        
        GDSpecialPriceBusiness gb = (GDSpecialPriceBusiness) business;
        String businessName = business.getName();
        
        if (gb.getReasonResult() == null) {
            return;
        }
        
        // 若当前问卷至少识别出一条有效的特价车结果，则记一次识别成功
        if (gb.getReasonResult().getSuppliersInfo() != null && 
            !gb.getReasonResult().getSuppliersInfo().isEmpty()) {
            if (metricsClient != null) {
                metricsClient.incrementCounter(RECOGNITION_SUCCESS_METRIC, 
                    Map.of("business", businessName));
            }
        } else {
            // 没有任何供应商，后续会在 MySQL sinker 中插入 MockPartner，这里记录一次"Mock"场景
            if (gb.getInput() != null && metricsClient != null) {
                metricsClient.incrementCounter(MOCK_PARTNER_METRIC, 
                    Map.of("business", businessName, 
                           "city", gb.getInput().getCityName() != null ? gb.getInput().getCityName() : "unknown"));
            }
        }
        
        // 检查价格是否在合理范围内
        if (gb.getReasonResult().getSuppliersInfo() != null) {
            for (ReasonSupplierResult supplier : gb.getReasonResult().getSuppliersInfo()) {
                if (supplier.getCapPrice() != null && 
                    (supplier.getCapPrice() < MIN_CAP_PRICE || supplier.getCapPrice() > MAX_CAP_PRICE)) {
                    if (metricsClient != null) {
                        metricsClient.incrementCounter(CAP_PRICE_OUT_OF_RANGE_METRIC, 
                            Map.of("business", businessName, 
                                   "supplier", supplier.getSupplier() != null ? supplier.getSupplier() : "unknown"));
                    }
                }
            }
        }
    }
}

