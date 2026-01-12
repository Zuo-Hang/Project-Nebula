# Nacos 配置初始化工具

## 📋 概述

`NacosConfigInitializer` 是一个用于批量将配置导入到 Nacos 配置中心的工具类。可以在应用启动前或上线时执行，一次性导入所有配置。

## 🎯 使用场景

1. **首次部署**: 将配置从 Apollo 迁移到 Nacos
2. **批量更新**: 一次性更新多个配置命名空间
3. **环境初始化**: 为新环境快速初始化配置
4. **配置备份恢复**: 从备份文件恢复配置

## 📦 功能特性

- ✅ 支持批量导入配置文件（Properties、YAML）
- ✅ 支持单个配置发布
- ✅ 支持从 Map 发布配置
- ✅ 支持配置删除
- ✅ 支持命令行参数和环境变量
- ✅ 自动映射配置命名空间

## 🚀 快速开始

### 1. 准备配置文件

在 `conf/nacos/` 目录下创建配置文件，命名格式：`{命名空间}.{扩展名}`

```bash
# 创建配置目录
mkdir -p conf/nacos

# 创建配置文件
cat > conf/nacos/OCR_LLM_CONF.properties << EOF
llm_cluster_conf_bsaas={"disfName":"disf!...","appId":"...","params":{...}}
bsaas_prompt=请识别图片中的司机和乘客信息...
EOF
```

### 2. 使用初始化脚本（推荐）

```bash
# 基本用法
./scripts/init-nacos-config.sh

# 指定参数
NACOS_SERVER_ADDR=127.0.0.1:8848 \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=nacos \
NACOS_NAMESPACE=your-namespace \
./scripts/init-nacos-config.sh
```

### 3. 使用 Java 主方法

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

### 4. 在代码中使用

```java
// 创建初始化器
NacosConfigInitializer initializer = new NacosConfigInitializer(
    "127.0.0.1:8848",  // serverAddr
    "",                 // namespace (可选)
    "nacos",           // username
    "nacos",           // password
    "DEFAULT_GROUP"     // group
);

// 批量导入配置
int count = initializer.initConfigs("./conf/nacos");
System.out.println("成功导入 " + count + " 个配置");

// 单个配置发布
Map<String, String> config = new HashMap<>();
config.put("key1", "value1");
config.put("key2", "value2");
initializer.publishConfigFromMap("OCR_LLM_CONF", config, "properties");

// 从文件发布
String content = Files.readString(Paths.get("config.properties"));
initializer.publishConfig("OCR_LLM_CONF", content, "properties");
```

## 📝 配置命名空间映射

工具会自动将文件名映射到配置命名空间：

| 文件名 | Data ID | 说明 |
|--------|---------|------|
| `OCR_LLM_CONF.properties` | OCR_LLM_CONF | LLM 配置 |
| `PRICE_FITTING_CONF.properties` | PRICE_FITTING_CONF | 价格拟合配置 |
| `QUALITY_MONITOR_CONF.properties` | QUALITY_MONITOR_CONF | 质量监控配置 |
| `OCR_BUSINESS_CONF.properties` | OCR_BUSINESS_CONF | OCR 业务配置 |

如果文件名不在映射表中，会使用文件名（去掉扩展名）作为 Data ID。

## 🔧 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--server-addr` | Nacos 服务器地址 | `127.0.0.1:8848` |
| `--namespace` | 命名空间（可选） | 空 |
| `--username` | 用户名 | `nacos` |
| `--password` | 密码 | `nacos` |
| `--group` | 配置组 | `DEFAULT_GROUP` |
| `--config-dir` | 配置文件目录 | `./conf/nacos` |

## 🌍 环境变量

也可以通过环境变量设置参数：

```bash
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=your-namespace
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos
export NACOS_CONFIG_DIR=./conf/nacos

./scripts/init-nacos-config.sh
```

## 📄 配置文件格式

### Properties 格式

```properties
# 注释
key1=value1
key2=value2
key3=value3 with spaces
```

### YAML 格式（简化版）

```yaml
key1: value1
key2: value2
key3: value3 with spaces
```

**注意**: 当前 YAML 支持是简化版，不支持复杂嵌套结构。如需复杂 YAML，建议使用 Properties 格式或 JSON。

## 🔄 从 Apollo 迁移配置

### 步骤 1: 导出 Apollo 配置

```bash
# 使用 Apollo 控制台或 API 导出配置
# 保存为 properties 文件到 conf/nacos/ 目录
```

### 步骤 2: 调整配置格式

```bash
# 确保配置文件命名正确
# OCR_LLM_CONF.properties
# PRICE_FITTING_CONF.properties
# ...
```

### 步骤 3: 导入到 Nacos

```bash
# 启动 Nacos（如果未启动）
cd docker && ./start.sh

# 等待 Nacos 启动完成（约 30-60 秒）

# 运行初始化脚本
./scripts/init-nacos-config.sh
```

### 步骤 4: 验证配置

1. 访问 Nacos 控制台: http://localhost:8848/nacos
2. 登录（默认用户名/密码: nacos/nacos）
3. 进入"配置管理" → "配置列表"
4. 验证配置是否正确导入

## ⚠️ 注意事项

1. **配置覆盖**: 如果配置已存在，会覆盖原有配置。建议先备份。

2. **敏感信息**: 不要将包含密码、密钥等敏感信息的配置文件提交到代码库。

3. **配置格式**: 确保配置文件格式正确，特别是 JSON 字符串中的转义字符。

4. **Nacos 连接**: 确保 Nacos 服务已启动并可访问。

5. **权限**: 确保有配置发布权限。

## 🐛 故障排查

### 问题 1: 连接失败

```
错误: 配置发布异常: Connection refused
```

**解决方案**:
- 检查 Nacos 服务是否启动
- 检查服务器地址和端口是否正确
- 检查网络连接

### 问题 2: 认证失败

```
错误: 配置发布异常: 401 Unauthorized
```

**解决方案**:
- 检查用户名和密码是否正确
- 检查 Nacos 是否启用了认证

### 问题 3: 配置格式错误

```
错误: 配置导入异常: Invalid format
```

**解决方案**:
- 检查配置文件格式是否正确
- 检查 JSON 字符串中的转义字符
- 查看详细错误日志

## 📚 相关文档

- [Nacos 配置中心迁移指南](./README_APOLLO_TO_NACOS.md)
- [Nacos 官方文档](https://nacos.io/docs/latest/guide/user/configuration/)
- [配置文件示例](../../../conf/nacos/README.md)

