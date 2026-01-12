# Nacos 配置类型说明

## 📋 概述

在将配置写入 Nacos 时，需要指定配置类型（Config Type），以便 Nacos 能够正确解析和管理配置内容。

## 🎯 支持的配置类型

Nacos 支持以下配置类型：

| 配置类型 | 文件扩展名 | 说明 |
|---------|-----------|------|
| `properties` | `.properties`, `.prop` | Java Properties 格式 |
| `yaml` | `.yaml`, `.yml` | YAML 格式 |
| `json` | `.json` | JSON 格式 |
| `xml` | `.xml` | XML 格式 |
| `text` | `.txt`, `.text` | 纯文本格式 |

## 🔧 自动类型识别

`NacosConfigInitializer` 会根据文件扩展名自动识别配置类型：

```java
// 自动识别配置类型
publishConfig("OCR_LLM_CONF", content, "properties");  // 类型: properties
publishConfig("OCR_LLM_CONF", content, "yaml");         // 类型: yaml
publishConfig("OCR_LLM_CONF", content, "json");         // 类型: json
```

## 📝 使用示例

### 方式 1: 从文件导入（自动识别类型）

```java
NacosConfigInitializer initializer = new NacosConfigInitializer(
    "127.0.0.1:8848", "", "nacos", "nacos", "DEFAULT_GROUP");

// 自动根据文件扩展名识别类型
initializer.initConfigs("./conf/nacos");
// OCR_LLM_CONF.properties -> 类型: properties
// OCR_BUSINESS_CONF.yaml   -> 类型: yaml
```

### 方式 2: 手动指定类型

```java
// 发布 Properties 配置
String propertiesContent = "key1=value1\nkey2=value2";
initializer.publishConfig("OCR_LLM_CONF", propertiesContent, "properties");

// 发布 YAML 配置
String yamlContent = "key1: value1\nkey2: value2";
initializer.publishConfig("OCR_LLM_CONF", yamlContent, "yaml");

// 发布 JSON 配置
String jsonContent = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
initializer.publishConfig("OCR_LLM_CONF", jsonContent, "json");
```

### 方式 3: 从 Map 发布（自动转换格式）

```java
Map<String, String> config = new HashMap<>();
config.put("key1", "value1");
config.put("key2", "value2");

// 转换为 Properties 格式
initializer.publishConfigFromMap("OCR_LLM_CONF", config, "properties");

// 转换为 YAML 格式
initializer.publishConfigFromMap("OCR_LLM_CONF", config, "yaml");

// 转换为 JSON 格式
initializer.publishConfigFromMap("OCR_LLM_CONF", config, "json");
```

## 🔍 配置类型映射

文件扩展名到配置类型的映射关系：

```java
private String convertExtensionToConfigType(String extension) {
    switch (extension.toLowerCase()) {
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
            return "properties"; // 默认类型
    }
}
```

## ⚠️ 注意事项

1. **类型一致性**: 同一个 Data ID 的配置类型应该保持一致
2. **默认类型**: 如果未指定类型或无法识别，默认使用 `properties`
3. **Nacos 控制台**: 在 Nacos 控制台中创建配置时，也需要选择正确的配置格式
4. **读取配置**: 读取配置时，Nacos 会根据配置类型自动解析

## 📚 相关文档

- [Nacos 配置中心文档](README_APOLLO_TO_NACOS.md)
- [配置初始化工具文档](README_NACOS_CONFIG_INIT.md)

