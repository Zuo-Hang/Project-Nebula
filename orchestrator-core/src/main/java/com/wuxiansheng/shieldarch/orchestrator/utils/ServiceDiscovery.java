package com.wuxiansheng.shieldarch.orchestrator.utils;

/**
 * 服务发现接口
 * 
 * 抽象服务发现功能，支持多种实现（Nacos、Consul、etcd 等）
 */
public interface ServiceDiscovery {
    
    /**
     * 获取 HTTP 端点
     * 
     * @param serviceName 服务名称（支持服务发现格式，如 "disf!service-name" 或直接 IP:Port）
     * @return HTTP 端点（格式：ip:port），如果获取失败返回 null
     */
    String getHttpEndpoint(String serviceName);
    
    /**
     * 检查服务发现是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();
}

