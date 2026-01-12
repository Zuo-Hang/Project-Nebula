# Nacos 服务发现替换 DiSF 说明

## 📋 概述

本项目已使用 **Nacos** 替换 **DiSF** 服务发现，提供更标准化的服务发现能力。

## 🏗️ 架构设计

```
ServiceDiscovery (接口)
    ↓
NacosServiceDiscovery (实现)
    ↓
DiSFUtils (兼容层，保持向后兼容)
    ↓
业务代码 (LLMClient, DiSFChatModel 等)
```

## 📦 核心组件

### 1. `ServiceDiscovery` 接口
- 抽象服务发现功能
- 支持多种实现（Nacos、Consul、Eureka 等）

### 2. `NacosServiceDiscovery` 实现
- 使用 Nacos 作为服务发现组件
- 支持命名空间、认证等配置
- 自动初始化和关闭

### 3. `DiSFUtils` 兼容层
- 保持向后兼容
- 内部使用 `ServiceDiscovery` 接口
- 标记为 `@Deprecated`，建议直接使用 `ServiceDiscovery`

## ⚙️ 配置

### application.yml

```yaml
nacos:
  enabled: true                    # 是否启用 Nacos（默认：true）
  server-addr: 127.0.0.1:8848     # Nacos 服务器地址
  namespace:                       # 命名空间（可选）
  username:                        # 用户名（可选，如果启用了认证）
  password:                        # 密码（可选，如果启用了认证）
```

### 环境变量

```bash
export NACOS_ENABLED=true
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=production
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos
```

## 🔧 使用方式

### 方式 1：直接使用 ServiceDiscovery（推荐）

```java
@Autowired
private ServiceDiscovery serviceDiscovery;

public void someMethod() {
    String endpoint = serviceDiscovery.getHttpEndpoint("disf!service-name");
    // 或
    String endpoint = serviceDiscovery.getHttpEndpoint("service-name");
}
```

### 方式 2：使用 DiSFUtils（兼容旧代码）

```java
@Autowired
private DiSFUtils diSFUtils;

public void someMethod() {
    String endpoint = diSFUtils.getHttpEndpoint("disf!service-name");
}
```

## 🔄 服务名称格式

### 支持两种格式

1. **带前缀格式**（兼容 DiSF）：
   ```
   disf!service-name
   ```
   - 自动移除 `disf!` 前缀
   - 在 Nacos 中查找 `service-name`

2. **直接格式**：
   ```
   service-name
   ```
   - 直接在 Nacos 中查找

3. **IP:Port 格式**（测试环境）：
   ```
   127.0.0.1:8080
   ```
   - 如果包含 `:` 且不是 `disf!` 格式，直接返回

## 📊 功能特性

### 1. 服务发现
- ✅ 自动从 Nacos 获取服务实例
- ✅ 支持健康检查（只返回健康实例）
- ✅ 支持负载均衡（返回多个实例）

### 2. 配置管理
- ✅ 支持命名空间隔离
- ✅ 支持用户名密码认证
- ✅ 支持多服务器地址（逗号分隔）

### 3. 兼容性
- ✅ 保持 DiSFUtils 接口不变
- ✅ 自动处理 `disf!` 前缀
- ✅ 支持测试环境 VIP 直连

## 🚀 迁移步骤

### 1. 安装 Nacos

```bash
# 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.3.0/nacos-server-2.3.0.tar.gz

# 解压
tar -xzf nacos-server-2.3.0.tar.gz

# 启动（单机模式）
cd nacos/bin
sh startup.sh -m standalone
```

### 2. 配置 Nacos

在 `application.yml` 中配置 Nacos 服务器地址：

```yaml
nacos:
  server-addr: 127.0.0.1:8848
```

### 3. 注册服务

服务提供方需要在 Nacos 中注册服务：

```java
// 示例：服务提供方注册到 Nacos
NamingService namingService = NamingFactory.createNamingService(properties);
Instance instance = new Instance();
instance.setIp("192.168.1.100");
instance.setPort(8080);
instance.setHealthy(true);
namingService.registerInstance("service-name", instance);
```

### 4. 使用服务发现

服务消费方（本项目）会自动从 Nacos 获取服务端点。

## ⚠️ 注意事项

1. **服务名称映射**
   - DiSF 格式：`disf!service-name` → Nacos 中查找 `service-name`
   - 需要确保服务在 Nacos 中注册的名称与 DiSF 名称一致（去掉前缀）

2. **命名空间**
   - 如果使用命名空间，需要确保服务注册和发现使用相同的命名空间

3. **健康检查**
   - 默认只返回健康的服务实例
   - 确保服务实例的健康检查正常

4. **向后兼容**
   - `DiSFUtils` 仍然可用，但建议迁移到 `ServiceDiscovery` 接口
   - 旧代码无需修改即可工作

## 🔍 调试和监控

### 日志

- Nacos 初始化：`Nacos 服务发现初始化成功`
- 服务发现：`从 Nacos 获取服务端点成功`
- 错误：`从 Nacos 获取服务端点失败`

### Nacos 控制台

访问 `http://127.0.0.1:8848/nacos` 查看：
- 服务列表
- 服务实例
- 健康状态

## 📚 相关文档

- [Nacos 官方文档](https://nacos.io/docs/latest/guide/user/quick-start.html)
- [Nacos Java SDK](https://nacos.io/docs/latest/guide/user/sdk.html)
- [Spring Cloud Alibaba Nacos](https://github.com/alibaba/spring-cloud-alibaba/wiki/Nacos-discovery)

## 🎯 后续优化

1. **负载均衡**
   - 实现轮询、随机等负载均衡策略
   - 支持权重配置

2. **服务缓存**
   - 缓存服务实例列表，减少 Nacos 查询
   - 支持缓存刷新

3. **监控指标**
   - 添加服务发现成功率指标
   - 添加服务发现延迟指标

