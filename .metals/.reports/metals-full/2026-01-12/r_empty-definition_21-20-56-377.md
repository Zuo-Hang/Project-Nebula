error id: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/io/DufeClient.java:_empty_/SupplierResponseRateMapper#getResponseRate#
file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/io/DufeClient.java
empty definition using pc, found symbol in pc: _empty_/SupplierResponseRateMapper#getResponseRate#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2151
uri: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/io/DufeClient.java
text:
```scala
package com.wuxiansheng.shieldarch.marsdata.io;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Dufe 客户端封装
 * 
 * 已替换为 MySQL 数据库查询，不再依赖 Dufe 服务
 * 数据存储在 supplier_response_rate 表中
 */
@Slf4j
@Component
public class DufeClient {
    
    @Autowired(required = false)
    private SupplierResponseRateMapper supplierResponseRateMapper;
    
    private boolean initialized = false;

    /**
     * 初始化Dufe服务（现在使用 MySQL）
     * 在 Spring 容器初始化后自动调用
     */
    @PostConstruct
    public void initDufeService() {
        if (supplierResponseRateMapper == null) {
            log.warn("[DufeClient] SupplierResponseRateMapper 未注入，Dufe功能将不可用");
            initialized = false;
            return;
        }
        
        initialized = true;
        log.info("[DufeClient] 已切换到 MySQL 数据源，使用 supplier_response_rate 表");
    }

    /**
     * 获取模板特征
     * 
     * @param ftId 模板ID（已废弃，保留用于兼容性）
     * @param params 参数，如 {"city_name": "北京市", "partner_name": "供应商A"}
     * @return 特征 Map，key 为特征名，value 为特征值（字符串格式）
     *         目前只返回 o_supplier_rate（供应商应答概率）
     */
    public Map<String, String> getTemplateFeature(String ftId, Map<String, String> params) {
        if (!initialized || supplierResponseRateMapper == null) {
            log.warn("[DufeClient] getTemplateFeature 未初始化，ftId={}, params={}，返回空结果", ftId, params);
            return new HashMap<>();
        }
        
        String cityName = params != null ? params.get("city_name") : null;
        String partnerName = params != null ? params.get("partner_name") : null;
        
        if (cityName == null || cityName.isEmpty() || partnerName == null || partnerName.isEmpty()) {
            log.warn("[DufeClient] getTemplateFeature 参数不完整，cityName={}, partnerName={}", cityName, partnerName);
            return new HashMap<>();
        }
        
        try {
            // 从 MySQL 查询应答概率
            BigDecimal responseRate = supplierResponseRateMapper.getR@@esponseRate(cityName, partnerName);
            
            if (responseRate == null) {
                log.debug("[DufeClient] getTemplateFeature 未找到数据: cityName={}, partnerName={}", cityName, partnerName);
                return new HashMap<>();
            }
            
            // 构造返回结果（兼容原有格式）
            Map<String, String> features = new HashMap<>();
            features.put("o_supplier_rate", responseRate.toString());
            
            log.debug("[DufeClient] getTemplateFeature 查询成功: cityName={}, partnerName={}, responseRate={}", 
                cityName, partnerName, responseRate);
            
            return features;
            
        } catch (Exception e) {
            log.error("[DufeClient] getTemplateFeature 查询失败: cityName={}, partnerName={}, error={}", 
                cityName, partnerName, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 检查 dufe 服务是否可用
     */
    public boolean isAvailable() {
        return initialized && supplierResponseRateMapper != null;
    }
}


```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/SupplierResponseRateMapper#getResponseRate#