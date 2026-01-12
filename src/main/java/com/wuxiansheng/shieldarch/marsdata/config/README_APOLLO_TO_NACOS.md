# Apollo → Nacos 配置中心替换说明

## 📋 概述

本项目已使用 **Nacos 配置中心**替换 **Apollo 配置中心**，提供更统一的配置管理能力。

## 🏗️ 架构设计

```
AppConfigService (接口)
    ↓
┌─────────────────┬─────────────────┐
│ NacosConfigService │ ApolloConfigService │
│ (新实现)        │ (兼容层)        │
└─────────────────┴─────────────────┘
    ↓                    ↓
Nacos Config API    Apollo Config API
```

## 📦 核心组件

### 1. `AppConfigService` 接口
- 抽象配置管理功能
- 定义统一的配置访问接口
- 支持多种实现（Nacos、Apollo 等）

### 2. `NacosConfigService` 实现
- 使用 Nacos Config API
- 支持配置热更新
- 支持 Properties 和 YAML 格式

### 3. `ApolloConfigService` 兼容层
- 保持向后兼容
- 内部优先使用 Nacos，回退到 Apollo
- 标记为 `@Deprecated`

## ⚙️ 配置

### application.yml

```yaml
# Nacos 配置（服务发现 + 配置中心）
nacos:
  enabled: true
  server-addr: 127.0.0.1:8848
  namespace:  # 可选
  username: nacos
  password: nacos
  config:
    enabled: true
    group: DEFAULT_GROUP
    app-id: project-nebula

# 配置服务选择（优先使用 Nacos）
config:
  use-nacos: true  # 设置为 false 可回退到 Apollo
```

## 🔄 使用方式

### 方式 1：使用 NacosConfigService（推荐）

```java
@Autowired
@Qualifier("nacosConfigService")
private NacosConfigService configService;

public void someMethod() {
    Map<String, String> config = configService.getConfig("OCR_LLM_CONF");
    String value = configService.getProperty("OCR_LLM_CONF", "key", "default");
}
```

### 方式 2：使用 ApolloConfigService（兼容旧代码）

```java
@Autowired
private ApolloConfigService apolloConfigService;

public void someMethod() {
    // 内部会自动使用 Nacos（如果启用）
    Map<String, String> config = apolloConfigService.getConfig("OCR_LLM_CONF");
}
```

### 方式 3：使用接口（最佳实践）

```java
@Autowired
private AppConfigService configService;

public void someMethod() {
    Map<String, String> config = configService.getConfig(AppConfigService.OCR_LLM_CONF);
}
```

## 📊 配置命名空间映射

### Apollo → Nacos 映射

| Apollo Namespace | Nacos Data ID | Nacos Group | 说明 |
|-----------------|---------------|-------------|------|
| OCR_LLM_CONF | OCR_LLM_CONF | DEFAULT_GROUP | LLM 配置 |
| PRICE_FITTING_CONF | PRICE_FITTING_CONF | DEFAULT_GROUP | 价格拟合配置 |
| QUALITY_MONITOR_CONF | QUALITY_MONITOR_CONF | DEFAULT_GROUP | 质量监控配置 |
| OCR_BUSINESS_CONF | OCR_BUSINESS_CONF | DEFAULT_GROUP | OCR 业务配置 |

## 🔧 在 Nacos 中创建配置

### 步骤 1：访问 Nacos 控制台

http://localhost:8848/nacos

### 步骤 2：创建配置

1. 进入 **配置管理** → **配置列表**
2. 点击 **+** 按钮
3. 填写配置信息：
   - **Data ID**: `OCR_LLM_CONF`（对应 Apollo 的 Namespace）
   - **Group**: `DEFAULT_GROUP`
   - **配置格式**: `Properties` 或 `YAML`
   - **配置内容**: 粘贴配置内容

### 步骤 3：配置格式示例

**Properties 格式**：
```properties
llm_cluster_conf_bsaas={"serviceName":"disf!...","appId":"...","params":{...}}
bsaas_prompt=请识别图片中的...
bsaas_valid_supplier=小拉出行,小拉特选,顺风车
```

**YAML 格式**：
```yaml
llm_cluster_conf_bsaas: '{"serviceName":"disf!...","appId":"...","params":{...}}'
bsaas_prompt: "请识别图片中的..."
bsaas_valid_supplier: "小拉出行,小拉特选,顺风车"
```

## ✨ 核心特性

### 1. 向后兼容
- ✅ 保持 `ApolloConfigService` 接口不变
- ✅ 现有代码无需修改
- ✅ 自动回退机制

### 2. 配置热更新
- ✅ 支持配置变更监听
- ✅ 自动刷新配置缓存
- ✅ 无需重启应用

### 3. 多格式支持
- ✅ Properties 格式
- ✅ YAML 格式（简化解析）

### 4. 配置缓存
- ✅ 本地缓存配置内容
- ✅ 减少 Nacos 查询
- ✅ 支持缓存刷新

## 🔄 迁移步骤

### 阶段 1：基础设施（已完成）

- ✅ 创建 `AppConfigService` 接口
- ✅ 实现 `NacosConfigService`
- ✅ 创建 `ApolloConfigService` 兼容层
- ✅ 添加配置项

### 阶段 2：配置迁移（待完成）

1. **在 Nacos 中创建配置命名空间**
   - OCR_LLM_CONF
   - PRICE_FITTING_CONF
   - QUALITY_MONITOR_CONF
   - OCR_BUSINESS_CONF

2. **迁移配置数据**
   - 从 Apollo 导出配置
   - 导入到 Nacos
   - 验证配置正确性

### 阶段 3：代码迁移（待完成）

1. **更新所有使用 ApolloConfigService 的文件**
   - 19 个文件需要更新
   - 建议逐步迁移，保持兼容

2. **测试验证**
   - 测试配置读取
   - 测试配置热更新
   - 测试回退机制

## ⚠️ 注意事项

1. **配置格式**
   - Nacos 支持 Properties 和 YAML
   - 当前实现支持简单的 key-value 解析
   - 复杂 YAML 结构需要升级解析逻辑

2. **配置监听**
   - 配置变更会自动更新缓存
   - 但业务代码需要重新读取配置才能生效
   - 建议使用配置监听器实现自动刷新

3. **向后兼容**
   - `ApolloConfigService` 仍然可用
   - 默认优先使用 Nacos
   - 可以通过 `config.use-nacos=false` 回退到 Apollo

4. **配置迁移**
   - 确保配置数据完整迁移
   - 验证配置格式正确性
   - 测试配置读取功能

## 🔍 调试和监控

### 日志

- Nacos 初始化：`Nacos 配置中心初始化成功`
- 配置读取：`获取 Nacos 配置成功`
- 配置更新：`Nacos 配置更新`
- 错误：`获取 Nacos 配置失败`

### Nacos 控制台

访问 `http://localhost:8848/nacos` 查看：
- 配置列表
- 配置内容
- 配置历史

## 📚 相关文档

- [Nacos 配置中心文档](https://nacos.io/docs/latest/guide/user/configuration/)
- [Nacos Java SDK](https://nacos.io/docs/latest/guide/user/sdk.html)
- [Apollo 替换清单](../DIDI_COMPONENTS_REPLACEMENT.md)

## 🎯 后续优化

1. **完善 YAML 解析**
   - 使用 SnakeYAML 库
   - 支持复杂 YAML 结构

2. **配置监听优化**
   - 实现配置变更通知
   - 自动刷新业务配置

3. **配置加密**
   - 支持敏感配置加密
   - 配置解密功能

4. **配置版本管理**
   - 支持配置版本回滚
   - 配置变更历史

