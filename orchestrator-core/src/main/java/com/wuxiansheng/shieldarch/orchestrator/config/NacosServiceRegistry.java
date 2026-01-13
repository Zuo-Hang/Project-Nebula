package com.wuxiansheng.shieldarch.orchestrator.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos 服务注册组件
 * 
 * 在应用启动时自动将当前服务注册到 Nacos，应用关闭时自动注销
 */
@Slf4j
@Component
public class NacosServiceRegistry implements ApplicationListener<WebServerInitializedEvent> {
    
    /**
     * Nacos 服务器地址（多个地址用逗号分隔）
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
     * 是否启用服务注册（默认启用）
     */
    @Value("${nacos.service-registry.enabled:true}")
    private boolean enabled;
    
    /**
     * 服务名称（默认使用 spring.application.name）
     */
    @Value("${nacos.service-registry.service-name:${spring.application.name}}")
    private String serviceName;
    
    /**
     * 服务组名（默认使用 DEFAULT_GROUP）
     */
    @Value("${nacos.service-registry.group-name:DEFAULT_GROUP}")
    private String groupName;
    
    /**
     * 服务 IP（默认自动获取本机 IP）
     */
    @Value("${nacos.service-registry.ip:}")
    private String ip;
    
    /**
     * 服务端口（默认使用应用 HTTP 端口）
     */
    private int port;
    
    /**
     * 服务实例权重（默认 1.0）
     */
    @Value("${nacos.service-registry.weight:1.0}")
    private double weight;
    
    /**
     * 是否启用健康检查（默认 true）
     */
    @Value("${nacos.service-registry.healthy:true}")
    private boolean healthy;
    
    /**
     * 是否启用临时实例（默认 true，临时实例会在服务下线时自动删除）
     */
    @Value("${nacos.service-registry.ephemeral:true}")
    private boolean ephemeral;
    
    /**
     * 服务元数据（可选）
     */
    @Value("${nacos.service-registry.metadata:}")
    private String metadata;
    
    private NamingService namingService;
    private boolean initialized = false;
    private boolean registered = false;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Nacos 服务注册未启用");
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
            
            log.info("Nacos 服务注册组件初始化成功: serverAddr={}, namespace={}", serverAddr, namespace);
            
        } catch (NacosException e) {
            log.error("Nacos 服务注册组件初始化失败: serverAddr={}, error={}", serverAddr, e.getMessage(), e);
            initialized = false;
        }
    }
    
    /**
     * 监听 Web 服务器初始化事件，获取 HTTP 端口
     */
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!enabled || !initialized) {
            return;
        }
        
        // 获取应用 HTTP 端口
        this.port = event.getWebServer().getPort();
        
        // 延迟注册，确保所有组件都已初始化
        try {
            Thread.sleep(1000); // 等待 1 秒，确保服务完全启动
            registerService();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("服务注册等待被中断", e);
        }
    }
    
    /**
     * 注册服务到 Nacos
     */
    public void registerService() {
        if (!enabled || !initialized || namingService == null) {
            log.warn("Nacos 服务注册未启用或未初始化，跳过注册");
            return;
        }
        
        if (registered) {
            log.warn("服务已注册，跳过重复注册: serviceName={}", serviceName);
            return;
        }
        
        try {
            // 获取服务 IP
            String serviceIp = getServiceIp();
            
            // 创建服务实例
            Instance instance = new Instance();
            instance.setIp(serviceIp);
            instance.setPort(port);
            instance.setWeight(weight);
            instance.setHealthy(healthy);
            instance.setEphemeral(ephemeral);
            
            // 设置元数据
            Map<String, String> instanceMetadata = new HashMap<>();
            instanceMetadata.put("version", "1.0.0-SNAPSHOT");
            instanceMetadata.put("spring.application.name", serviceName);
            if (metadata != null && !metadata.isEmpty()) {
                // 解析 metadata（格式：key1=value1,key2=value2）
                String[] pairs = metadata.split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        instanceMetadata.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
            instance.setMetadata(instanceMetadata);
            
            // 注册服务实例
            namingService.registerInstance(serviceName, groupName, instance);
            registered = true;
            
            log.info("服务注册成功: serviceName={}, groupName={}, ip={}, port={}, weight={}, healthy={}, ephemeral={}", 
                serviceName, groupName, serviceIp, port, weight, healthy, ephemeral);
            
        } catch (NacosException e) {
            log.error("服务注册失败: serviceName={}, error={}", serviceName, e.getMessage(), e);
            registered = false;
        } catch (Exception e) {
            log.error("服务注册异常: serviceName={}, error={}", serviceName, e.getMessage(), e);
            registered = false;
        }
    }
    
    /**
     * 注销服务
     */
    @PreDestroy
    public void deregisterService() {
        if (!enabled || !initialized || namingService == null || !registered) {
            return;
        }
        
        try {
            String serviceIp = getServiceIp();
            namingService.deregisterInstance(serviceName, groupName, serviceIp, port);
            registered = false;
            
            log.info("服务注销成功: serviceName={}, groupName={}, ip={}, port={}", 
                serviceName, groupName, serviceIp, port);
            
        } catch (NacosException e) {
            log.error("服务注销失败: serviceName={}, error={}", serviceName, e.getMessage(), e);
        } catch (Exception e) {
            log.error("服务注销异常: serviceName={}, error={}", serviceName, e.getMessage(), e);
        } finally {
            // 关闭 NamingService
            if (namingService != null) {
                try {
                    namingService.shutDown();
                    log.info("Nacos NamingService 已关闭");
                } catch (NacosException e) {
                    log.error("关闭 Nacos NamingService 失败: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 获取服务 IP 地址
     */
    private String getServiceIp() {
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        
        try {
            // 自动获取本机 IP
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            log.warn("获取本机 IP 失败，使用默认值 127.0.0.1: {}", e.getMessage());
            return "127.0.0.1";
        }
    }
    
    /**
     * 手动触发服务注册（用于测试或特殊场景）
     */
    public boolean registerServiceManually(String serviceName, String ip, int port) {
        if (!enabled || !initialized || namingService == null) {
            log.warn("Nacos 服务注册未启用或未初始化");
            return false;
        }
        
        try {
            Instance instance = new Instance();
            instance.setIp(ip);
            instance.setPort(port);
            instance.setWeight(weight);
            instance.setHealthy(healthy);
            instance.setEphemeral(ephemeral);
            
            namingService.registerInstance(serviceName, groupName, instance);
            log.info("手动注册服务成功: serviceName={}, ip={}, port={}", serviceName, ip, port);
            return true;
            
        } catch (NacosException e) {
            log.error("手动注册服务失败: serviceName={}, error={}", serviceName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查服务是否已注册
     */
    public boolean isRegistered() {
        return registered;
    }
}

