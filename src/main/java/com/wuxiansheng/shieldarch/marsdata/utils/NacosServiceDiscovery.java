package com.wuxiansheng.shieldarch.marsdata.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Properties;

/**
 * Nacos 服务发现实现
 * 
 * 使用 Nacos 作为服务发现组件
 */
@Slf4j
@Component
public class NacosServiceDiscovery implements ServiceDiscovery {
    
    /**
     * Nacos 服务器地址（多个地址用逗号分隔）
     * 例如：127.0.0.1:8848,127.0.0.1:8849
     */
    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;
    
    /**
     * Nacos 命名空间（可选）
     */
    @Value("${nacos.namespace:}")
    private String namespace;
    
    /**
     * Nacos 用户名（可选，如果启用了认证）
     */
    @Value("${nacos.username:}")
    private String username;
    
    /**
     * Nacos 密码（可选，如果启用了认证）
     */
    @Value("${nacos.password:}")
    private String password;
    
    /**
     * 是否启用 Nacos（默认启用）
     */
    @Value("${nacos.enabled:true}")
    private boolean enabled;
    
    private NamingService namingService;
    private boolean initialized = false;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Nacos 服务发现未启用");
            return;
        }
        
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            
            if (namespace != null && !namespace.isEmpty()) {
                properties.put("namespace", namespace);
            }
            
            if (username != null && !username.isEmpty()) {
                properties.put("username", username);
            }
            
            if (password != null && !password.isEmpty()) {
                properties.put("password", password);
            }
            
            namingService = NamingFactory.createNamingService(properties);
            initialized = true;
            
            log.info("Nacos 服务发现初始化成功: serverAddr={}, namespace={}", serverAddr, namespace);
            
        } catch (NacosException e) {
            log.error("Nacos 服务发现初始化失败: serverAddr={}, error={}", serverAddr, e.getMessage(), e);
            initialized = false;
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (namingService != null) {
            try {
                namingService.shutDown();
                log.info("Nacos 服务发现已关闭");
            } catch (NacosException e) {
                log.error("关闭 Nacos 服务发现失败: {}", e.getMessage(), e);
            }
        }
    }
    
    @Override
    public String getHttpEndpoint(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("服务名称为空");
            return null;
        }
        
        // 兼容测试环境的 VIP（不包含 disf! 前缀）
        // 如果已经是 ip:port 格式，直接返回
        if (!serviceName.contains("disf!") && serviceName.contains(":")) {
            log.debug("使用测试环境 VIP: {}", serviceName);
            return serviceName;
        }
        
        // 移除 disf! 前缀（如果存在）
        String cleanServiceName = serviceName.replace("disf!", "");
        
        if (!enabled || !initialized || namingService == null) {
            log.warn("Nacos 服务发现未启用或未初始化，无法获取端点: {}", cleanServiceName);
            return null;
        }
        
        try {
            // 获取所有健康的服务实例
            List<Instance> instances = namingService.getAllInstances(cleanServiceName, true);
            
            if (instances == null || instances.isEmpty()) {
                log.warn("Nacos 中未找到服务实例: {}", cleanServiceName);
                return null;
            }
            
            // 选择第一个健康的实例
            Instance instance = instances.get(0);
            String endpoint = instance.getIp() + ":" + instance.getPort();
            
            log.debug("从 Nacos 获取服务端点成功: serviceName={}, endpoint={}", cleanServiceName, endpoint);
            
            return endpoint;
            
        } catch (NacosException e) {
            log.error("从 Nacos 获取服务端点失败: serviceName={}, error={}", cleanServiceName, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && initialized && namingService != null;
    }
    
    /**
     * 获取服务实例列表（用于负载均衡等场景）
     * 
     * @param serviceName 服务名称
     * @param healthy 是否只返回健康的实例
     * @return 服务实例列表
     */
    public List<Instance> getInstances(String serviceName, boolean healthy) {
        if (!isAvailable()) {
            log.warn("Nacos 服务发现不可用");
            return null;
        }
        
        String cleanServiceName = serviceName.replace("disf!", "");
        
        try {
            return namingService.getAllInstances(cleanServiceName, healthy);
        } catch (NacosException e) {
            log.error("获取服务实例列表失败: serviceName={}, error={}", cleanServiceName, e.getMessage(), e);
            return null;
        }
    }
}

