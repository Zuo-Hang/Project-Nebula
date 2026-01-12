# Nacos 配置文件目录

## 📋 说明

本目录用于存放需要导入到 Nacos 配置中心的配置文件。

## 📁 文件命名规则

配置文件命名格式：`{命名空间}.{扩展名}`

例如：
- `OCR_LLM_CONF.properties` → Data ID: `OCR_LLM_CONF`, Group: `DEFAULT_GROUP`
- `PRICE_FITTING_CONF.yaml` → Data ID: `PRICE_FITTING_CONF`, Group: `DEFAULT_GROUP`

## 📝 支持的格式

- **Properties**: `.properties`
- **YAML**: `.yaml` 或 `.yml`

## 🔧 配置命名空间

| 文件名 | Data ID | 说明 |
|--------|---------|------|
| `OCR_LLM_CONF.properties` | OCR_LLM_CONF | LLM 配置 |
| `PRICE_FITTING_CONF.properties` | PRICE_FITTING_CONF | 价格拟合配置 |
| `QUALITY_MONITOR_CONF.properties` | QUALITY_MONITOR_CONF | 质量监控配置 |
| `OCR_BUSINESS_CONF.properties` | OCR_BUSINESS_CONF | OCR 业务配置 |

## 📄 配置文件示例

### OCR_LLM_CONF.properties

```properties
# LLM 集群配置
llm_cluster_conf_bsaas={"disfName":"disf!...","appId":"...","params":{"model":"...","maxTokens":8192,"temperature":0.3}}

# Prompt 配置
bsaas_prompt=请识别图片中的司机和乘客信息...
bsaas_passenger_prompt=请识别图片中的乘客信息...
bsaas_driver_prompt=请识别图片中的司机信息...

# 供应商验证
bsaas_valid_supplier=小拉出行,小拉特选,顺风车
gd_valid_supplier=曹操出行,添猫出行,AA出行,...

# 其他配置
llm_local_concurrent_0=150
llm_local_concurrent_1=70
llm_local_concurrent_2=50
```

### PRICE_FITTING_CONF.properties

```properties
# 价格拟合相关配置
price_fitting_enabled=true
price_fitting_interval=3600
```

## 🚀 使用方法

### 方式 1: 使用初始化脚本（推荐）

```bash
# 1. 准备配置文件
# 将配置文件放到 conf/nacos/ 目录

# 2. 运行初始化脚本
./scripts/init-nacos-config.sh

# 或指定参数
NACOS_SERVER_ADDR=127.0.0.1:8848 \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=nacos \
./scripts/init-nacos-config.sh
```

### 方式 2: 使用 Java 主方法

```bash
# 编译项目
mvn compile

# 运行初始化工具
java -cp target/classes:target/dependency/* \
    com.wuxiansheng.shieldarch.marsdata.config.NacosConfigInitializer \
    --server-addr=127.0.0.1:8848 \
    --username=nacos \
    --password=nacos \
    --config-dir=./conf/nacos
```

### 方式 3: 在代码中调用

```java
NacosConfigInitializer initializer = new NacosConfigInitializer(
    "127.0.0.1:8848", "", "nacos", "nacos", "DEFAULT_GROUP");

// 批量导入
int count = initializer.initConfigs("./conf/nacos");

// 或单个导入
Map<String, String> config = new HashMap<>();
config.put("key1", "value1");
config.put("key2", "value2");
initializer.publishConfigFromMap("OCR_LLM_CONF", config, "properties");
```

## ⚠️ 注意事项

1. **配置文件格式**
   - Properties: `key=value` 格式
   - YAML: `key: value` 格式（简化版，不支持复杂结构）

2. **配置覆盖**
   - 如果配置已存在，会覆盖原有配置
   - 建议先备份现有配置

3. **配置验证**
   - 导入后建议在 Nacos 控制台验证
   - 确保配置格式正确

4. **敏感信息**
   - 不要将包含密码、密钥等敏感信息的配置文件提交到代码库
   - 使用环境变量或加密配置

## 📚 相关文档

- [Nacos 配置中心文档](../../src/main/java/com/wuxiansheng/shieldarch/marsdata/config/README_APOLLO_TO_NACOS.md)
- [Nacos 官方文档](https://nacos.io/docs/latest/guide/user/configuration/)

