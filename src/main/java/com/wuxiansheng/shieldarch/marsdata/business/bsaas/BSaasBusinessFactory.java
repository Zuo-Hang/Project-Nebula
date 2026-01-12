package com.wuxiansheng.shieldarch.marsdata.business.bsaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * B SaaS业务工厂
 */
@Slf4j
@Component
public class BSaasBusinessFactory implements BusinessFactory {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public Business createBusiness(String msg) {
        try {
            BSaasInput input = objectMapper.readValue(msg, BSaasInput.class);
            
            if (input.getSubmitDate() != null && !input.getSubmitDate().isEmpty()) {
                try {
                    LocalDate date = LocalDate.parse(input.getSubmitDate(), 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    input.setSubmitDateTime(date.atStartOfDay());
                } catch (Exception e) {
                    log.warn("BSaasBusinessFactory.CreateBusiness err: {}, submit_date: {}", 
                        e.getMessage(), input.getSubmitDate());
                    return null;
                }
            }
            
            BSaasBusiness business = new BSaasBusiness();
            business.setInput(input);
            
            return business;
            
        } catch (Exception e) {
            log.warn("BSaasBusinessFactory CreateBusiness err: {}, msg: {}", e.getMessage(), msg);
            return null;
        }
    }
}

