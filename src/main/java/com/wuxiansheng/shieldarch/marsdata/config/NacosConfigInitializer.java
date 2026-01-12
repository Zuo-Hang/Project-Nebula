package com.wuxiansheng.shieldarch.marsdata.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos 配置初始化工具
 * 
 * 用于批量将配置导入到 Nacos 配置中心
 * 可以在应用启动前或上线时执行，一次性导入所有配置
 * 
 * 使用方式：
 * 1. 准备配置文件（Properties 或 YAML 格式）
 * 2. 调用 initConfigs() 方法批量导入
 * 3. 或使用 main 方法作为独立脚本运行
 */
@Slf4j
public class NacosConfigInitializer {
    
    @SuppressWarnings("unused")
    private final String serverAddr;
    @SuppressWarnings("unused")
    private final String namespace;
    @SuppressWarnings("unused")
    private final String username;
    @SuppressWarnings("unused")
    private final String password;
    private final String group;
    private final ConfigService configService;
    
    /**
     * 构造函数
     * 
     * @param serverAddr Nacos 服务器地址
     * @param namespace 命名空间（可选）
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param group 配置组（默认：DEFAULT_GROUP）
     */
    public NacosConfigInitializer(String serverAddr, String namespace, 
                                  String username, String password, String group) 
            throws NacosException {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.username = username;
        this.password = password;
        this.group = group != null && !group.isEmpty() ? group : "DEFAULT_GROUP";
        
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
        
        this.configService = NacosFactory.createConfigService(properties);
    }
    
    /**
     * 初始化所有配置
     * 
     * 从配置目录读取配置文件并批量导入到 Nacos
     * 
     * @param configDir 配置文件目录（如：./conf/nacos）
     * @return 成功导入的配置数量
     */
    public int initConfigs(String configDir) throws IOException, NacosException {
        Path configPath = Paths.get(configDir);
        if (!Files.exists(configPath) || !Files.isDirectory(configPath)) {
            log.warn("配置目录不存在: {}", configDir);
            return 0;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        // 定义配置命名空间映射
        Map<String, String> namespaceMapping = new HashMap<>();
        namespaceMapping.put("OCR_LLM_CONF.properties", AppConfigService.OCR_LLM_CONF);
        namespaceMapping.put("OCR_LLM_CONF.yaml", AppConfigService.OCR_LLM_CONF);
        namespaceMapping.put("OCR_LLM_CONF.yml", AppConfigService.OCR_LLM_CONF);
        namespaceMapping.put("PRICE_FITTING_CONF.properties", AppConfigService.PRICE_FITTING_CONF);
        namespaceMapping.put("PRICE_FITTING_CONF.yaml", AppConfigService.PRICE_FITTING_CONF);
        namespaceMapping.put("PRICE_FITTING_CONF.yml", AppConfigService.PRICE_FITTING_CONF);
        namespaceMapping.put("QUALITY_MONITOR_CONF.properties", AppConfigService.QUALITY_MONITOR_CONF);
        namespaceMapping.put("QUALITY_MONITOR_CONF.yaml", AppConfigService.QUALITY_MONITOR_CONF);
        namespaceMapping.put("QUALITY_MONITOR_CONF.yml", AppConfigService.QUALITY_MONITOR_CONF);
        namespaceMapping.put("OCR_BUSINESS_CONF.properties", AppConfigService.OCR_BUSINESS_CONF);
        namespaceMapping.put("OCR_BUSINESS_CONF.yaml", AppConfigService.OCR_BUSINESS_CONF);
        namespaceMapping.put("OCR_BUSINESS_CONF.yml", AppConfigService.OCR_BUSINESS_CONF);
        
        // 遍历配置文件
        File[] files = configPath.toFile().listFiles((dir, name) -> 
            name.endsWith(".properties") || name.endsWith(".yaml") || name.endsWith(".yml"));
        
        if (files == null || files.length == 0) {
            log.warn("配置目录中没有找到配置文件: {}", configDir);
            return 0;
        }
        
        for (File file : files) {
            String fileName = file.getName();
            String dataId = namespaceMapping.get(fileName);
            
            if (dataId == null) {
                // 如果不在映射表中，使用文件名（去掉扩展名）作为 Data ID
                int lastDot = fileName.lastIndexOf('.');
                dataId = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
            }
            
            try {
                String content = Files.readString(file.toPath());
                boolean success = publishConfig(dataId, content, getFileExtension(fileName));
                
                if (success) {
                    successCount++;
                    log.info("配置导入成功: dataId={}, file={}", dataId, fileName);
                } else {
                    failCount++;
                    log.error("配置导入失败: dataId={}, file={}", dataId, fileName);
                }
            } catch (Exception e) {
                failCount++;
                log.error("配置导入异常: dataId={}, file={}, error={}", dataId, fileName, e.getMessage(), e);
            }
        }
        
        log.info("配置初始化完成: 成功={}, 失败={}, 总计={}", 
            successCount, failCount, successCount + failCount);
        
        return successCount;
    }
    
    /**
     * 发布单个配置
     * 
     * @param dataId Data ID（对应配置命名空间）
     * @param content 配置内容
     * @param fileExtension 文件扩展名（properties, yaml, yml, json, xml）
     * @return 是否成功
     */
    public boolean publishConfig(String dataId, String content, String fileExtension) {
        try {
            // 将文件扩展名转换为 Nacos 配置类型
            String configType = convertExtensionToConfigType(fileExtension);
            
            // 使用指定类型的 publishConfig 方法
            // Nacos 2.x API: publishConfig(String dataId, String group, String content, String type)
            boolean success = configService.publishConfig(dataId, group, content, configType);
            
            if (success) {
                log.info("配置发布成功: dataId={}, group={}, type={}, extension={}", 
                    dataId, group, configType, fileExtension);
            } else {
                log.warn("配置发布失败: dataId={}, group={}, type={}", dataId, group, configType);
            }
            return success;
        } catch (NacosException e) {
            log.error("配置发布异常: dataId={}, type={}, error={}", 
                dataId, convertExtensionToConfigType(fileExtension), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 将文件扩展名转换为 Nacos 配置类型
     * 
     * @param extension 文件扩展名（properties, yaml, yml, json, xml）
     * @return Nacos 配置类型（properties, yaml, json, xml, text）
     */
    private String convertExtensionToConfigType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "properties"; // 默认类型
        }
        
        String ext = extension.toLowerCase().trim();
        
        switch (ext) {
            case "properties":
            case "prop":
                return "properties";
            case "yaml":
            case "yml":
                return "yaml";
            case "json":
                return "json";
            case "xml":
                return "xml";
            case "txt":
            case "text":
                return "text";
            default:
                log.warn("未知的文件扩展名: {}, 使用默认类型 properties", extension);
                return "properties";
        }
    }
    
    /**
     * 发布配置（从 Map）
     * 
     * @param dataId Data ID
     * @param configMap 配置 Map
     * @param format 格式（properties, yaml, yml, json）
     * @return 是否成功
     */
    public boolean publishConfigFromMap(String dataId, Map<String, String> configMap, String format) {
        String content;
        String formatLower = format.toLowerCase();
        
        if (formatLower.equals("yaml") || formatLower.equals("yml")) {
            content = convertMapToYaml(configMap);
        } else if (formatLower.equals("json")) {
            content = convertMapToJson(configMap);
        } else {
            // 默认使用 properties
            content = convertMapToProperties(configMap);
        }
        
        return publishConfig(dataId, content, format);
    }
    
    /**
     * 将 Map 转换为 JSON 格式
     */
    private String convertMapToJson(Map<String, String> configMap) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(configMap);
        } catch (Exception e) {
            log.error("转换 Map 为 JSON 失败: {}", e.getMessage(), e);
            return "{}";
        }
    }
    
    /**
     * 删除配置
     * 
     * @param dataId Data ID
     * @return 是否成功
     */
    public boolean removeConfig(String dataId) {
        try {
            boolean success = configService.removeConfig(dataId, group);
            if (success) {
                log.info("配置删除成功: dataId={}, group={}", dataId, group);
            }
            return success;
        } catch (NacosException e) {
            log.error("配置删除异常: dataId={}, error={}", dataId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取配置
     * 
     * @param dataId Data ID
     * @return 配置内容
     */
    public String getConfig(String dataId) throws NacosException {
        return configService.getConfig(dataId, group, 5000);
    }
    
    /**
     * 关闭连接
     */
    public void shutdown() {
        // Nacos ConfigService 没有显式的关闭方法
        // 可以设置为 null 或让 GC 回收
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "properties";
    }
    
    /**
     * 将 Map 转换为 Properties 格式
     */
    private String convertMapToProperties(Map<String, String> configMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 将 Map 转换为 YAML 格式（简化版）
     */
    private String convertMapToYaml(Map<String, String> configMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 主方法：作为独立脚本运行
     * 
     * 使用方式：
     * java -cp ... NacosConfigInitializer \
     *   --server-addr=127.0.0.1:8848 \
     *   --namespace= \
     *   --username=nacos \
     *   --password=nacos \
     *   --config-dir=./conf/nacos
     */
    public static void main(String[] args) {
        String serverAddr = "127.0.0.1:8848";
        String namespace = "";
        String username = "nacos";
        String password = "nacos";
        String group = "DEFAULT_GROUP";
        String configDir = "./conf/nacos";
        
        // 解析命令行参数
        for (String arg : args) {
            if (arg.startsWith("--server-addr=")) {
                serverAddr = arg.substring("--server-addr=".length());
            } else if (arg.startsWith("--namespace=")) {
                namespace = arg.substring("--namespace=".length());
            } else if (arg.startsWith("--username=")) {
                username = arg.substring("--username=".length());
            } else if (arg.startsWith("--password=")) {
                password = arg.substring("--password=".length());
            } else if (arg.startsWith("--group=")) {
                group = arg.substring("--group=".length());
            } else if (arg.startsWith("--config-dir=")) {
                configDir = arg.substring("--config-dir=".length());
            }
        }
        
        // 也可以从环境变量读取
        serverAddr = System.getenv().getOrDefault("NACOS_SERVER_ADDR", serverAddr);
        namespace = System.getenv().getOrDefault("NACOS_NAMESPACE", namespace);
        username = System.getenv().getOrDefault("NACOS_USERNAME", username);
        password = System.getenv().getOrDefault("NACOS_PASSWORD", password);
        configDir = System.getenv().getOrDefault("NACOS_CONFIG_DIR", configDir);
        
        try {
            log.info("开始初始化 Nacos 配置...");
            log.info("服务器地址: {}", serverAddr);
            log.info("命名空间: {}", namespace.isEmpty() ? "(默认)" : namespace);
            log.info("配置目录: {}", configDir);
            
            NacosConfigInitializer initializer = new NacosConfigInitializer(
                serverAddr, namespace, username, password, group);
            
            int count = initializer.initConfigs(configDir);
            
            log.info("配置初始化完成，成功导入 {} 个配置", count);
            
        } catch (Exception e) {
            log.error("配置初始化失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

