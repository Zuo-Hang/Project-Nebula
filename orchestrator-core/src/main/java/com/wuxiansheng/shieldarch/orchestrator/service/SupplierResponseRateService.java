package com.wuxiansheng.shieldarch.orchestrator.service;

import com.wuxiansheng.shieldarch.orchestrator.mapper.SupplierResponseRateMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 供应商应答概率服务
 * 
 * 从 MySQL 数据库查询供应商应答概率
 * 数据存储在 supplier_response_rate 表中
 */
@Slf4j
@Service
public class SupplierResponseRateService {
    
    @Autowired(required = false)
    private SupplierResponseRateMapper supplierResponseRateMapper;
    
    private boolean initialized = false;

    /**
     * 初始化服务
     * 在 Spring 容器初始化后自动调用
     */
    @PostConstruct
    public void init() {
        if (supplierResponseRateMapper == null) {
            log.warn("[SupplierResponseRateService] SupplierResponseRateMapper 未注入，服务将不可用");
            initialized = false;
            return;
        }
        
        initialized = true;
        log.info("[SupplierResponseRateService] 已初始化，使用 supplier_response_rate 表查询数据");
    }

    /**
     * 获取供应商应答概率
     * 
     * @param params 参数，如 {"city_name": "北京市", "partner_name": "供应商A"}
     * @return 特征 Map，key 为特征名，value 为特征值（字符串格式）
     *         返回 o_supplier_rate（供应商应答概率）
     */
    public Map<String, String> getResponseRate(Map<String, String> params) {
        if (!initialized || supplierResponseRateMapper == null) {
            log.warn("[SupplierResponseRateService] getResponseRate 未初始化，params={}，返回空结果", params);
            return new HashMap<>();
        }
        
        String cityName = params != null ? params.get("city_name") : null;
        String partnerName = params != null ? params.get("partner_name") : null;
        
        if (cityName == null || cityName.isEmpty() || partnerName == null || partnerName.isEmpty()) {
            log.warn("[SupplierResponseRateService] getResponseRate 参数不完整，cityName={}, partnerName={}", cityName, partnerName);
            return new HashMap<>();
        }
        
        try {
            // 从 MySQL 查询应答概率
            BigDecimal responseRate = supplierResponseRateMapper.getResponseRate(cityName, partnerName);
            
            if (responseRate == null) {
                log.debug("[SupplierResponseRateService] getResponseRate 未找到数据: cityName={}, partnerName={}", cityName, partnerName);
                return new HashMap<>();
            }
            
            // 构造返回结果（兼容原有格式）
            Map<String, String> features = new HashMap<>();
            features.put("o_supplier_rate", responseRate.toString());
            
            log.debug("[SupplierResponseRateService] getResponseRate 查询成功: cityName={}, partnerName={}, responseRate={}", 
                cityName, partnerName, responseRate);
            
            return features;
            
        } catch (Exception e) {
            log.error("[SupplierResponseRateService] getResponseRate 查询失败: cityName={}, partnerName={}, error={}", 
                cityName, partnerName, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 检查服务是否可用
     */
    public boolean isAvailable() {
        return initialized && supplierResponseRateMapper != null;
    }
}

