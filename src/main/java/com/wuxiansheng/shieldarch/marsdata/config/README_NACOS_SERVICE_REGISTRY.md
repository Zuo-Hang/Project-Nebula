# Nacos 服务注册使用说明

## 📋 概述

`NacosServiceRegistry` 组件会在应用启动时自动将当前服务注册到 Nacos，应用关闭时自动注销。支持自动获取服务 IP 和端口，也支持手动配置。

## 🚀 快速开始

### 1. 启用服务注册

在 `application.yml` 中配置：

```yaml
nacos:
  enabled: true
  server-addr: 127.0.0.1:8848
  service-registry:
    enabled: true  # 启用服务注册
```

### 2. 基本配置

```yaml
nacos:
  service-registry:
    enabled: true
    service-name: ${spring.application.name}  # 服务名称（默认使用 spring.application.name）
    group-name: DEFAULT_GROUP  # 服务组名
    ip: ""  # 服务 IP（留空则自动获取本机 IP）
    weight: 1.0  # 服务权重
    healthy: true  # 是否健康
    ephemeral: true  # 是否临时实例（临时实例会在服务下线时自动删除）
    metadata: ""  # 元数据（格式：key1=value1,key2=value2）
```

### 3. 环境变量配置

```bash
# 启用服务注册
export NACOS_SERVICE_REGISTRY_ENABLED=true

# 服务名称
export NACOS_SERVICE_REGISTRY_SERVICE_NAME=project-nebula

# 服务组名
export NACOS_SERVICE_REGISTRY_GROUP_NAME=DEFAULT_GROUP

# 服务 IP（留空则自动获取）
export NACOS_SERVICE_REGISTRY_IP=

# 服务权重
export NACOS_SERVICE_REGISTRY_WEIGHT=1.0

# 是否健康
export NACOS_SERVICE_REGISTRY_HEALTHY=true

# 是否临时实例
export NACOS_SERVICE_REGISTRY_EPHEMERAL=true

# 元数据
export NACOS_SERVICE_REGISTRY_METADATA=version=1.0.0,env=prod
```

## 📝 配置说明

### 服务名称（service-name）

- **默认值**: `${spring.application.name}`（即 `Project-Nebula`）
- **说明**: 注册到 Nacos 的服务名称
- **示例**: `project-nebula`

### 服务组名（group-name）

- **默认值**: `DEFAULT_GROUP`
- **说明**: Nacos 服务分组，用于区分不同环境或不同业务
- **示例**: `DEFAULT_GROUP`、`PROD_GROUP`、`TEST_GROUP`

### 服务 IP（ip）

- **默认值**: 空（自动获取本机 IP）
- **说明**: 服务 IP 地址。如果留空，会自动获取本机 IP 地址
- **示例**: `192.168.1.100` 或留空自动获取

### 服务端口（port）

- **默认值**: 自动获取应用 HTTP 端口
- **说明**: 服务端口，会自动从 Spring Boot 的 `WebServerInitializedEvent` 中获取
- **注意**: 无需手动配置，会自动获取

### 服务权重（weight）

- **默认值**: `1.0`
- **说明**: 服务权重，用于负载均衡。权重越大，被选中的概率越高
- **示例**: `1.0`、`2.0`、`0.5`

### 健康状态（healthy）

- **默认值**: `true`
- **说明**: 服务是否健康。只有健康的服务才会被服务发现返回
- **示例**: `true`、`false`

### 临时实例（ephemeral）

- **默认值**: `true`
- **说明**: 
  - `true`: 临时实例，服务下线时会自动从 Nacos 删除
  - `false`: 持久实例，服务下线时不会自动删除，需要手动删除
- **推荐**: 生产环境建议使用 `true`（临时实例）

### 元数据（metadata）

- **默认值**: 空
- **说明**: 服务元数据，用于存储额外的服务信息（如版本、环境等）
- **格式**: `key1=value1,key2=value2`
- **示例**: `version=1.0.0,env=prod,region=beijing`

## 🔄 工作流程

### 启动时

1. **初始化阶段**（`@PostConstruct`）:
   - 创建 `NamingService` 连接
   - 初始化配置参数

2. **HTTP 服务器启动后**（`WebServerInitializedEvent`）:
   - 获取应用 HTTP 端口
   - 自动获取本机 IP（如果未配置）
   - 注册服务到 Nacos

### 关闭时

1. **应用关闭**（`@PreDestroy`）:
   - 从 Nacos 注销服务
   - 关闭 `NamingService` 连接

## 📊 服务注册示例

### 示例 1: 基本配置

```yaml
nacos:
  server-addr: 127.0.0.1:8848
  service-registry:
    enabled: true
    service-name: project-nebula
```

**结果**: 服务会以 `project-nebula` 名称注册到 Nacos，IP 和端口自动获取。

### 示例 2: 指定 IP 和权重

```yaml
nacos:
  server-addr: 127.0.0.1:8848
  service-registry:
    enabled: true
    service-name: project-nebula
    ip: 192.168.1.100
    weight: 2.0
```

**结果**: 服务以指定 IP 和权重 2.0 注册。

### 示例 3: 添加元数据

```yaml
nacos:
  server-addr: 127.0.0.1:8848
  service-registry:
    enabled: true
    service-name: project-nebula
    metadata: version=1.0.0,env=prod,region=beijing
```

**结果**: 服务注册时会包含元数据信息。

### 示例 4: 多环境配置

**开发环境** (`application-dev.yml`):
```yaml
nacos:
  service-registry:
    service-name: project-nebula-dev
    group-name: DEV_GROUP
    metadata: env=dev,version=1.0.0-SNAPSHOT
```

**生产环境** (`application-prod.yml`):
```yaml
nacos:
  service-registry:
    service-name: project-nebula
    group-name: PROD_GROUP
    metadata: env=prod,version=1.0.0
```

## 🔍 验证服务注册

### 1. 查看 Nacos 控制台

访问 Nacos 控制台（默认 `http://127.0.0.1:8848/nacos`），在"服务管理" -> "服务列表"中查看注册的服务。

### 2. 使用 Nacos API

```bash
# 查询服务实例列表
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=project-nebula&namespaceId="

# 查询服务详情
curl "http://127.0.0.1:8848/nacos/v1/ns/service?serviceName=project-nebula&namespaceId="
```

### 3. 查看应用日志

应用启动时会输出服务注册日志：

```
INFO  NacosServiceRegistry - 服务注册成功: serviceName=project-nebula, groupName=DEFAULT_GROUP, ip=192.168.1.100, port=8080, weight=1.0, healthy=true, ephemeral=true
```

## 🛠️ 高级用法

### 手动注册服务

如果需要手动注册其他服务，可以注入 `NacosServiceRegistry`：

```java
@Autowired
private NacosServiceRegistry nacosServiceRegistry;

public void registerCustomService() {
    boolean success = nacosServiceRegistry.registerServiceManually(
        "custom-service", 
        "192.168.1.100", 
        9090
    );
    if (success) {
        log.info("自定义服务注册成功");
    }
}
```

### 检查服务注册状态

```java
@Autowired
private NacosServiceRegistry nacosServiceRegistry;

public void checkRegistration() {
    if (nacosServiceRegistry.isRegistered()) {
        log.info("服务已注册");
    } else {
        log.warn("服务未注册");
    }
}
```

## ⚠️ 注意事项

1. **服务名称唯一性**: 确保同一组内服务名称唯一，否则会覆盖已有服务实例
2. **临时实例**: 使用临时实例时，服务下线会自动删除，无需手动清理
3. **持久实例**: 使用持久实例时，服务下线不会自动删除，需要手动清理
4. **健康检查**: Nacos 会定期检查服务健康状态，不健康的服务不会被服务发现返回
5. **网络问题**: 如果 Nacos 服务器不可用，服务注册会失败，但不会影响应用启动

## 🔗 相关文档

- [Nacos 服务发现使用说明](../utils/README_NACOS_SERVICE_DISCOVERY.md)
- [Nacos 配置中心使用说明](README_NACOS_CONFIG_INIT.md)
- [应用初始化顺序说明](AppInitializationOrder.java)

