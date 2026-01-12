package com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster;

import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import com.wuxiansheng.shieldarch.marsdata.monitor.StatsdClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按白名单过滤供应商
 */
@Slf4j
@Component
public class GDFilterSupplier implements Poster {
    
    @Autowired
    private LLMConfigHelper llmConfigHelper;
    
    @Autowired(required = false)
    private StatsdClient statsdClient;
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof GDBubbleBusiness)) {
            return business;
        }
        
        GDBubbleBusiness gb = (GDBubbleBusiness) business;
        
        if (gb.getReasonResult() == null || gb.getReasonResult().getSuppliersInfo() == null) {
            return gb;
        }
        
        List<ReasonSupplierResult> validSuppliers = new ArrayList<>();
        String businessName = business.getName();
        
        for (ReasonSupplierResult supplierInfo : gb.getReasonResult().getSuppliersInfo()) {
            if (llmConfigHelper != null && llmConfigHelper.isValidGDSupplier(supplierInfo.getSupplier())) {
                validSuppliers.add(supplierInfo);
            } else {
                // 上报被过滤的供应商
                if (statsdClient != null) {
                    statsdClient.incrementCounter("filtered_partner", 
                        Map.of("business", businessName, "partner", supplierInfo.getSupplier()));
                }
                log.info("FilterValidPartner filter partner name: {}, business_name: {}", 
                    supplierInfo.getSupplier(), businessName);
            }
        }
        
        gb.getReasonResult().setSuppliersInfo(validSuppliers);
        return gb;
    }
}

