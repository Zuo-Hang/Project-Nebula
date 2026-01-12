error id: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/config/NacosConfigService.java
file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/config/NacosConfigService.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[3,51]

error in qdox parser
file content:
```java
offset: 104
uri: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/config/NacosConfigService.java
text:
```scala
package com.wuxiansheng.shieldarch.marsdata.config;

import com.alibaba.nacos.api.config.ConfigService a@@s NacosConfigServiceApi;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Nacos 配置服务实现
 * 
 * 使用 Nacos 配置中心替换 Apollo
 */
@Slf4j
@Service("nacosConfigService")
public class NacosConfigService implements AppConfigService {
    
    /**
     * Nacos ConfigService API（使用别名避免冲突）
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
     * Nacos 应用ID（对应 Apollo 的 App ID）
     */
    @Value("${nacos.config.app-id:llm-data-collect}")
    private String appId;
    
    private NacosConfigServiceApi nacosConfigService;
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
            nacosConfigService = com.alibaba.nacos.api.NacosFactory.createConfigService(properties);
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
        for (Map.Entry<String, Listener> entry : listenerCache.entrySet()) {
            try {
                String dataId = entry.getKey();
                Listener listener = entry.getValue();
                nacosConfigService.removeListener(dataId, group, listener);
            } catch (NacosException e) {
                log.warn("移除 Nacos 配置监听器失败: dataId={}, error={}", entry.getKey(), e.getMessage());
            }
        }
        listenerCache.clear();
        configCache.clear();
        
        if (nacosConfigService != null) {
            try {
                // Nacos ConfigService 没有显式的关闭方法，但可以设置为 null
                nacosConfigService = null;
            } catch (Exception e) {
                log.warn("关闭 Nacos 配置服务失败: {}", e.getMessage());
            }
        }
        
        log.info("Nacos 配置中心已关闭");
    }
    
    @Override
    public Map<String, String> getConfig(String namespace) {
        Map<String, String> result = new HashMap<>();
        
        if (!enabled || !initialized || nacosConfigService == null) {
            log.warn("Nacos 配置中心未启用或未初始化，无法获取配置: namespace={}", namespace);
            return result;
        }
        
        try {
            // Nacos 中，Data ID 对应 Apollo 的 Namespace
            // 格式：{appId}-{namespace}.{fileExtension}
            // 为了兼容，我们使用 namespace 作为 Data ID
            String dataId = buildDataId(namespace);
            
            // 从缓存获取
            String configContent = configCache.get(dataId);
            if (configContent == null) {
                // 从 Nacos 获取配置
                configContent = nacosConfigService.getConfig(dataId, group, 5000);
                if (configContent != null) {
                    configCache.put(dataId, configContent);
                    
                    // 添加配置监听器（支持热更新）
                    addConfigListener(dataId);
                }
            }
            
            if (configContent == null || configContent.isEmpty()) {
                log.warn("Nacos 配置为空: dataId={}, group={}", dataId, group);
                return result;
            }
            
            // 解析配置内容（支持 properties 和 yaml 格式）
            result = parseConfigContent(configContent);
            
            log.debug("获取 Nacos 配置成功: namespace={}, dataId={}, count={}", 
                namespace, dataId, result.size());
            
        } catch (NacosException e) {
            log.error("获取 Nacos 配置失败: namespace={}, error={}", namespace, e.getMessage(), e);
        }
        
        return result;
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
     * 支持 properties 和 yaml 格式
     */
    private Map<String, String> parseConfigContent(String content) {
        Map<String, String> result = new HashMap<>();
        
        if (content == null || content.isEmpty()) {
            return result;
        }
        
        // 判断格式（简单判断，根据实际配置调整）
        if (content.contains("=") && !content.contains(":")) {
            // Properties 格式
            return parseProperties(content);
        } else {
            // YAML 格式（简化解析，只支持 key: value 格式）
            return parseYaml(content);
        }
    }
    
    /**
     * 解析 Properties 格式
     */
    private Map<String, String> parseProperties(String content) {
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
            
            nacosConfigService.addListener(dataId, group, listener);
            listenerCache.put(dataId, listener);
            
            log.debug("添加 Nacos 配置监听器成功: dataId={}, group={}", dataId, group);
            
        } catch (NacosException e) {
            log.error("添加 Nacos 配置监听器失败: dataId={}, error={}", dataId, e.getMessage(), e);
        }
    }
}


```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:677)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:674)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:674)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:912)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/config/NacosConfigService.java