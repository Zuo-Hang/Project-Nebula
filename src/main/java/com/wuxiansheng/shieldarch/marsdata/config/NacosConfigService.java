package com.wuxiansheng.shieldarch.marsdata.config;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Nacos 配置服务实现
 * 
 * 使用 Nacos 配置中心进行配置管理
 */
@Slf4j
@Service("nacosConfigService")
public class NacosConfigService implements AppConfigService {
    
    /**
     * Nacos ConfigService API（使用完整类名避免冲突）
     */
    private com.alibaba.nacos.api.config.ConfigService nacosConfigServiceApi;
    
    /**
     * Nacos 服务器地址
     */
    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;
    
    /**
     * Nacos 命名空间（可选）
     */
    @Value("${nacos.namespace:}")
    private String namespace;
    
    /**
     * Nacos 用户名（可选）
     */
    @Value("${nacos.username:nacos}")
    private String username;
    
    /**
     * Nacos 密码（可选）
     */
    @Value("${nacos.password:nacos}")
    private String password;
    
    /**
     * Nacos 配置组（默认：DEFAULT_GROUP）
     */
    @Value("${nacos.config.group:DEFAULT_GROUP}")
    private String group;
    
    /**
     * 是否启用 Nacos 配置（默认启用）
     */
    @Value("${nacos.config.enabled:true}")
    private boolean enabled;
    
    /**
     * 是否启用本地配置回退（默认启用）
     */
    @Value("${nacos.config.fallback-to-local:true}")
    private boolean fallbackToLocal;
    
    /**
     * Nacos 应用ID
     */
    @Value("${nacos.config.app-id:llm-data-collect}")
    private String appId;
    
    private boolean initialized = false;
    
    /**
     * 配置缓存（Data ID -> 配置内容）
     */
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    
    /**
     * 配置监听器缓存（用于取消监听）
     */
    private final Map<String, Listener> listenerCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Nacos 配置中心未启用");
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
            
            // 创建 Nacos ConfigService
            nacosConfigServiceApi = com.alibaba.nacos.api.NacosFactory.createConfigService(properties);
            initialized = true;
            
            log.info("Nacos 配置中心初始化成功: serverAddr={}, namespace={}, appId={}", 
                serverAddr, namespace, appId);
            
        } catch (NacosException e) {
            log.error("Nacos 配置中心初始化失败: serverAddr={}, error={}", serverAddr, e.getMessage(), e);
            initialized = false;
        }
    }
    
    @PreDestroy
    public void destroy() {
        // 移除所有监听器
        if (nacosConfigServiceApi != null) {
            for (Map.Entry<String, Listener> entry : listenerCache.entrySet()) {
                try {
                    String dataId = entry.getKey();
                    Listener listener = entry.getValue();
                    nacosConfigServiceApi.removeListener(dataId, group, listener);
                } catch (Exception e) {
                    log.warn("移除 Nacos 配置监听器失败: dataId={}, error={}", entry.getKey(), e.getMessage());
                }
            }
        }
        listenerCache.clear();
        configCache.clear();
        
        if (nacosConfigServiceApi != null) {
            try {
                // Nacos ConfigService 没有显式的关闭方法，但可以设置为 null
                nacosConfigServiceApi = null;
            } catch (Exception e) {
                log.warn("关闭 Nacos 配置服务失败: {}", e.getMessage());
            }
        }
        
        log.info("Nacos 配置中心已关闭");
    }
    
    @Override
    public Map<String, String> getConfig(String namespace) {
        Map<String, String> result = new HashMap<>();
        
        // 1. 尝试从 Nacos 获取配置
        if (enabled && initialized && nacosConfigServiceApi != null) {
            try {
                String dataId = buildDataId(namespace);
                
                // 从缓存获取
                String configContent = configCache.get(dataId);
                if (configContent == null) {
                    // 从 Nacos 获取配置
                    configContent = nacosConfigServiceApi.getConfig(dataId, group, 5000);
                    if (configContent != null) {
                        configCache.put(dataId, configContent);
                        // 添加配置监听器（支持热更新）
                        addConfigListener(dataId);
                    }
                }
                
                if (configContent != null && !configContent.isEmpty()) {
                    // 解析配置内容（支持 properties 和 yaml 格式）
                    result = parseConfigContent(configContent);
                    log.debug("从 Nacos 获取配置成功: namespace={}, dataId={}, count={}", 
                        namespace, dataId, result.size());
                    return result;
                }
            } catch (Exception e) {
                log.warn("从 Nacos 获取配置失败: namespace={}, error={}", namespace, e.getMessage());
            }
        }
        
        // 2. 如果 Nacos 不可用或配置为空，尝试从本地配置文件加载（回退方案）
        if (fallbackToLocal) {
            result = loadFromLocalFile(namespace);
            if (!result.isEmpty()) {
                log.info("从本地配置文件加载配置: namespace={}, count={}", namespace, result.size());
                return result;
            }
        }
        
        log.warn("无法获取配置: namespace={}, Nacos={}, Local={}", 
            namespace, enabled && initialized, fallbackToLocal);
        
        return result;
    }
    
    /**
     * 从本地配置文件加载配置（回退方案）
     * 
     * 支持多种格式：properties, yaml, yml, json
     * 按优先级尝试加载
     * 
     * @param namespace 配置命名空间
     * @return 配置Map
     */
    private Map<String, String> loadFromLocalFile(String namespace) {
        // 按优先级尝试加载不同格式的配置文件
        String[] extensions = {"properties", "yaml", "yml", "json"};
        
        for (String ext : extensions) {
            String configPath = "config/" + namespace + "." + ext;
            Resource resource = new ClassPathResource(configPath);
            
            if (resource.exists()) {
                try {
                    String content = new String(resource.getInputStream().readAllBytes(), 
                        StandardCharsets.UTF_8);
                    
                    // 根据扩展名选择解析方法
                    Map<String, String> config = parseConfigByExtension(content, ext);
                    if (!config.isEmpty()) {
                        log.debug("从本地配置文件加载配置成功: path={}, format={}, count={}", 
                            configPath, ext, config.size());
                        return config;
                    }
                } catch (IOException e) {
                    log.debug("加载本地配置文件失败: path={}, error={}", configPath, e.getMessage());
                }
            }
        }
        
        log.debug("本地配置文件不存在: namespace={}, 尝试的格式: {}", namespace, 
            String.join(", ", extensions));
        
        return new HashMap<>();
    }
    
    /**
     * 根据文件扩展名解析配置内容
     */
    private Map<String, String> parseConfigByExtension(String content, String extension) {
        String ext = extension.toLowerCase();
        
        switch (ext) {
            case "properties":
            case "prop":
                return parseProperties(content);
            case "yaml":
            case "yml":
                return parseYaml(content);
            case "json":
                return parseJson(content);
            default:
                log.warn("未知的配置文件格式: {}, 尝试作为 Properties 解析", extension);
                return parseProperties(content);
        }
    }
    
    @Override
    public String getProperty(String namespace, String key, String defaultValue) {
        Map<String, String> config = getConfig(namespace);
        return config.getOrDefault(key, defaultValue);
    }
    
    @Override
    public String getProperty(String namespace, String key) {
        return getProperty(namespace, key, null);
    }
    
    @Override
    public boolean hasProperty(String namespace, String key) {
        Map<String, String> config = getConfig(namespace);
        return config.containsKey(key);
    }
    
    /**
     * 构建 Data ID
     * 
     * Nacos 中，Data ID 的格式通常是：{appId}-{namespace}.{fileExtension}
     * 为了简化，我们直接使用 namespace 作为 Data ID
     */
    private String buildDataId(String namespace) {
        // 方案1: 直接使用 namespace
        return namespace;
        
        // 方案2: 使用 appId 前缀（如果需要）
        // return appId + "-" + namespace;
    }
    
    /**
     * 解析配置内容
     * 
     * 支持 properties、yaml 和 json 格式
     */
    private Map<String, String> parseConfigContent(String content) {
        Map<String, String> result = new HashMap<>();
        
        if (content == null || content.isEmpty()) {
            return result;
        }
        
        String trimmed = content.trim();
        
        // 1. 尝试解析为 JSON（以 { 开头，} 结尾）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return parseJson(content);
            } catch (Exception e) {
                log.debug("解析 JSON 格式失败，尝试其他格式: {}", e.getMessage());
            }
        }
        
        // 2. 判断是否为 Properties 格式（包含 = 且不包含 :，或者包含多个 =）
        if (content.contains("=") && (content.split("=").length > content.split(":").length || !content.contains(":"))) {
            return parseProperties(content);
        }
        
        // 3. 默认尝试 YAML 格式（简化解析，只支持 key: value 格式）
        return parseYaml(content);
    }
    
    /**
     * 解析 JSON 格式
     * 
     * 注意：JSON 格式的配置应该是 flat 的 key-value 对象
     */
    private Map<String, String> parseJson(String content) {
        Map<String, String> result = new HashMap<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> typeRef = 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {};
            Map<String, Object> jsonMap = objectMapper.readValue(content, typeRef);
            
            // 将 Object 转换为 String
            for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    result.put(entry.getKey(), value.toString());
                }
            }
        } catch (Exception e) {
            log.error("解析 JSON 格式失败: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 解析 Properties 格式
     * 
     * 使用标准的 Properties.load() 方法，支持转义字符
     */
    private Map<String, String> parseProperties(String content) {
        Map<String, String> result = new HashMap<>();
        
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            properties.forEach((key, value) -> 
                result.put(key.toString(), value.toString())
            );
        } catch (IOException e) {
            log.error("解析 Properties 格式失败: {}", e.getMessage(), e);
            // 回退到简单解析
            return parsePropertiesSimple(content);
        }
        
        return result;
    }
    
    /**
     * 简单的 Properties 解析（回退方案）
     */
    private Map<String, String> parsePropertiesSimple(String content) {
        Map<String, String> result = new HashMap<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int equalIndex = line.indexOf('=');
            if (equalIndex > 0) {
                String key = line.substring(0, equalIndex).trim();
                String value = line.substring(equalIndex + 1).trim();
                // 移除可能的引号
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * 解析 YAML 格式（简化版）
     * 
     * 注意：这是简化实现，只支持简单的 key: value 格式
     * 如果需要完整的 YAML 支持，可以使用 SnakeYAML 库
     */
    private Map<String, String> parseYaml(String content) {
        Map<String, String> result = new HashMap<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                // 移除可能的引号
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * 添加配置监听器（支持热更新）
     */
    private void addConfigListener(String dataId) {
        if (listenerCache.containsKey(dataId)) {
            return; // 已经添加过监听器
        }
        
        try {
            Listener listener = new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Nacos 配置更新: dataId={}, group={}", dataId, group);
                    // 更新缓存
                    configCache.put(dataId, configInfo);
                }
                
                @Override
                public Executor getExecutor() {
                    return null; // 使用默认执行器
                }
            };
            
            nacosConfigServiceApi.addListener(dataId, group, listener);
            listenerCache.put(dataId, listener);
            
            log.debug("添加 Nacos 配置监听器成功: dataId={}, group={}", dataId, group);
            
        } catch (NacosException e) {
            log.error("添加 Nacos 配置监听器失败: dataId={}, error={}", dataId, e.getMessage(), e);
        }
    }
}

