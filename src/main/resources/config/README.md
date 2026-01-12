# 本地配置文件目录

## 📋 说明

本目录用于存放本地配置文件，作为 Nacos 配置中心的回退方案。

## 🎯 使用场景

1. **本地开发**: Nacos 未启动时，使用本地配置
2. **测试环境**: 快速测试，无需配置 Nacos
3. **回退方案**: Nacos 配置中心不可用时，自动使用本地配置

## 📁 配置文件

配置文件命名格式：`{命名空间}.properties`

| 文件名 | 命名空间 | 说明 |
|--------|---------|------|
| `OCR_LLM_CONF.properties` | OCR_LLM_CONF | LLM 配置 |
| `OCR_BUSINESS_CONF.properties` | OCR_BUSINESS_CONF | 业务配置 |
| `PRICE_FITTING_CONF.properties` | PRICE_FITTING_CONF | 价格拟合配置 |
| `QUALITY_MONITOR_CONF.properties` | QUALITY_MONITOR_CONF | 质量监控配置 |

## 🔄 配置优先级

1. **Nacos 配置中心**（如果可用）
2. **本地配置文件**（回退方案）

## 📝 配置格式

使用标准的 Properties 格式：

```properties
# 注释
key1=value1
key2=value2

# JSON 字符串配置
llm_cluster_conf_bsaas={"disfName":"...","appId":"...","params":{...}}

# 列表配置（JSON 数组格式）
price_fitting_opened_cities=["北京市","上海市"]
```

## ⚠️ 注意事项

1. **敏感信息**: 不要将包含密码、密钥等敏感信息的配置文件提交到代码库
2. **配置同步**: 本地配置应与 Nacos 配置保持一致
3. **环境差异**: 不同环境可能需要不同的配置值

## 🚀 使用方法

### 方式 1: 直接编辑配置文件

```bash
# 编辑配置文件
vim src/main/resources/config/OCR_LLM_CONF.properties

# 添加配置项
llm_cluster_conf_bsaas={"disfName":"...","appId":"..."}
```

### 方式 2: 从 Nacos 导出配置

```bash
# 使用 Nacos 控制台导出配置
# 或使用 NacosConfigInitializer 工具导出

# 将导出的配置保存到对应文件
cp exported-config.properties src/main/resources/config/OCR_LLM_CONF.properties
```

## 📚 相关文档

- [Nacos 配置中心文档](../java/com/wuxiansheng/shieldarch/marsdata/config/README_APOLLO_TO_NACOS.md)
- [配置初始化工具文档](../java/com/wuxiansheng/shieldarch/marsdata/config/README_NACOS_CONFIG_INIT.md)

