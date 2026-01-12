package com.wuxiansheng.shieldarch.marsdata.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * DiSF服务发现工具类（兼容层）
 * 
 * 为了保持向后兼容，保留 DiSFUtils 类，但内部使用 ServiceDiscovery 接口
 * 默认使用 NacosServiceDiscovery，也可以通过配置切换到其他实现
 * 
 * @deprecated 建议直接使用 ServiceDiscovery 接口
 */
@Slf4j
@Component
@Deprecated
public class DiSFUtils {
    
    @Autowired(required = false)
    private ServiceDiscovery serviceDiscovery;
    
    /**
     * 获取HTTP端点
     * 
     * @param disfName DiSF服务名称（如 "disf!service-name"）
     * @return HTTP端点（格式：ip:port），如果获取失败返回null
     */
    public String getHttpEndpoint(String disfName) {
        if (serviceDiscovery != null) {
            return serviceDiscovery.getHttpEndpoint(disfName);
        }
        
        // 如果没有配置服务发现，使用兼容逻辑
        if (disfName == null || disfName.isEmpty()) {
            log.warn("服务名称为空");
            return null;
        }
        
        // 兼容测试环境的 VIP（不包含 disf! 前缀）
        if (!disfName.contains("disf!")) {
            log.debug("使用测试环境 VIP: {}", disfName);
            return disfName;
        }
        
        log.warn("服务发现未配置，无法获取端点: {}", disfName);
        log.warn("请配置 Nacos 或其他服务发现组件");
        
        return null;
    }
    
    /**
     * 检查服务发现是否可用
     * 
     * @return 是否可用
     */
    public boolean isAvailable() {
        return serviceDiscovery != null && serviceDiscovery.isAvailable();
    }
}

