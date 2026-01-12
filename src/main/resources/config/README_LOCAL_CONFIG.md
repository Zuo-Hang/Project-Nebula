# 本地配置文件使用说明

## 📋 概述

本地配置文件位于 `src/main/resources/config/` 目录，作为 Nacos 配置中心的回退方案。

## 🎯 使用场景

1. **本地开发**: Nacos 未启动时，自动使用本地配置
2. **测试环境**: 快速测试，无需配置 Nacos
3. **回退方案**: Nacos 配置中心不可用时，自动使用本地配置

## 📁 配置文件位置

```
src/main/resources/config/
├── OCR_LLM_CONF.properties          # LLM 配置
├── OCR_BUSINESS_CONF.properties     # 业务配置
├── PRICE_FITTING_CONF.properties    # 价格拟合配置
├── QUALITY_MONITOR_CONF.properties  # 质量监控配置
└── README.md                        # 说明文档
```

## 🔄 配置优先级

1. **Nacos 配置中心**（如果可用且配置存在）
2. **本地配置文件**（回退方案，从 `classpath:config/` 加载）

## 📝 配置格式

使用标准的 Properties 格式：

```properties
# 注释
key1=value1
key2=value2

# JSON 字符串配置
llm_cluster_conf_bsaas={"disfName":"disf!service-name","appId":"app-id","params":{...}}

# 列表配置（JSON 数组格式）
price_fitting_opened_cities=["北京市","上海市"]
```

## 🚀 使用方法

### 1. 编辑配置文件

直接编辑 `src/main/resources/config/` 目录下的配置文件：

```bash
# 编辑 LLM 配置
vim src/main/resources/config/OCR_LLM_CONF.properties

# 添加配置项
llm_cluster_conf_bsaas={"disfName":"disf!...","appId":"...","params":{...}}
bsaas_prompt=请识别图片中的司机和乘客信息...
```

### 2. 从 Nacos 导出配置

如果 Nacos 中已有配置，可以导出后保存到本地：

```bash
# 使用 Nacos 控制台导出配置
# 或使用 NacosConfigInitializer 工具导出

# 将导出的配置保存到对应文件
cp exported-config.properties src/main/resources/config/OCR_LLM_CONF.properties
```

### 3. 启动应用

应用启动时会自动：
1. 尝试连接 Nacos 配置中心
2. 如果 Nacos 不可用，自动从本地配置文件加载
3. 如果本地配置文件也不存在，返回空配置（使用代码中的默认值）

## ⚙️ 配置项

在 `application.yml` 中可以控制本地配置回退行为：

```yaml
nacos:
  config:
    enabled: true              # 是否启用 Nacos 配置中心
    fallback-to-local: true    # 是否启用本地配置回退（默认：true）
```

## 📋 配置文件示例

### OCR_LLM_CONF.properties

```properties
# LLM 集群配置
llm_cluster_conf_bsaas={"disfName":"disf!service-name","appId":"app-id","params":{"model":"/path/to/model","maxTokens":8192,"temperature":0.3}}

# Prompt 配置
bsaas_prompt=请识别图片中的司机和乘客信息，提取以下字段：...
bsaas_passenger_prompt=请识别图片中的乘客信息...
bsaas_driver_prompt=请识别图片中的司机信息...

# 供应商验证
bsaas_valid_supplier=小拉出行,小拉特选,顺风车
gd_valid_supplier=曹操出行,添猫出行,AA出行,900出行,...

# 并发控制
llm_local_concurrent_0=150
llm_local_concurrent_1=70
llm_local_concurrent_2=50
```

### OCR_BUSINESS_CONF.properties

```properties
# 业务配置（JSON 格式）
business_b_saas={"name":"b_saas","enable":true,"max_concurrent":30,"sources":[{"unique_id":"B-SAAS","level":1,"is_test":false}]}
business_gd_bubble={"name":"gd_bubble","enable":false,"max_concurrent":0,"sources":[{"unique_id":"ddpage_0I5ObjQ8","level":1,"is_test":false}]}
```

## ⚠️ 注意事项

1. **敏感信息**: 不要将包含密码、密钥等敏感信息的配置文件提交到代码库
2. **配置同步**: 本地配置应与 Nacos 配置保持一致
3. **环境差异**: 不同环境可能需要不同的配置值
4. **配置格式**: 确保 JSON 字符串格式正确，特别是转义字符

## 🔍 调试

查看日志可以了解配置加载情况：

```
# Nacos 可用时
从 Nacos 获取配置成功: namespace=OCR_LLM_CONF, dataId=OCR_LLM_CONF, count=10

# Nacos 不可用时
从本地配置文件加载配置: namespace=OCR_LLM_CONF, count=10

# 都不可用时
无法获取配置: namespace=OCR_LLM_CONF, Nacos=false, Local=true
```

## 📚 相关文档

- [Nacos 配置中心文档](../../java/com/wuxiansheng/shieldarch/marsdata/config/README_APOLLO_TO_NACOS.md)
- [配置初始化工具文档](../../java/com/wuxiansheng/shieldarch/marsdata/config/README_NACOS_CONFIG_INIT.md)

