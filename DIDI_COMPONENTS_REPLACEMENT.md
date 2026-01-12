# 滴滴内部组件替换清单

## 📋 当前状态

### ✅ 已替换

| 组件 | 原实现 | 替换方案 | 状态 |
|------|--------|---------|------|
| **DiSF** | 滴滴服务发现 | **Nacos** | ✅ 已完成 |
| - | DiSFUtils | NacosServiceDiscovery | ✅ 已完成 |

### ⚠️ 待替换

| 组件 | 用途 | 当前状态 | 推荐替换方案 | 优先级 |
|------|------|---------|------------|--------|
| **Apollo** | 配置中心 | 使用中 | **Nacos 配置中心** | 🔴 高 |
| **DirPC** | 未知（可能是 RPC 框架） | 占位实现 | **gRPC** 或 **Dubbo** | 🟡 中 |
| **Dufe** | 特征服务 | 占位实现 | **自定义 HTTP 服务** 或 **Feature Store** | 🟡 中 |
| **Odin** | 监控平台 | 占位实现 | **Prometheus + Grafana** 或 **Spring Boot Actuator** | 🟢 低 |

### 📝 遗留代码（可清理）

| 组件 | 文件 | 说明 |
|------|------|------|
| **DiSFInitializer** | `config/DiSFInitializer.java` | 已替换为 Nacos，可删除或标记废弃 |

---

## 🔴 高优先级：Apollo 配置中心

### 当前使用情况

- **使用位置**: 19 个文件
- **主要功能**:
  - LLM 配置管理（`OCR_LLM_CONF`）
  - 价格拟合配置（`PRICE_FITTING_CONF`）
  - 业务配置（`OCR_BUSINESS_CONF`）
  - Prompt 管理
  - 供应商验证配置

### 替换方案：Nacos 配置中心

**优势**：
- ✅ 已使用 Nacos 做服务发现，可以复用
- ✅ 支持配置管理功能
- ✅ 支持配置热更新
- ✅ 支持多环境配置

**实现步骤**：
1. 使用 Nacos 配置管理 API
2. 创建 `NacosConfigService` 替换 `ApolloConfigService`
3. 迁移配置数据到 Nacos
4. 更新所有调用 `ApolloConfigService` 的地方

---

## 🟡 中优先级：DirPC

### 当前状态

- **文件**: `config/DirPCInitializer.java`
- **状态**: 占位实现，未真正使用
- **用途**: 未知（可能是滴滴内部的 RPC 框架）

### 替换方案

**方案 1: gRPC**（推荐）
- 标准化的 RPC 框架
- 跨语言支持
- 高性能

**方案 2: Dubbo**
- Java 生态成熟
- 服务治理完善
- 如果已有 Dubbo 基础设施

**方案 3: Spring Cloud OpenFeign**
- 与 Spring Boot 集成好
- 基于 HTTP，简单易用

**建议**: 如果 DirPC 未实际使用，可以先删除相关代码。

---

## 🟡 中优先级：Dufe 特征服务

### 当前状态

- **文件**: `io/DufeClient.java`
- **状态**: 占位实现，返回空结果
- **用途**: 获取模板特征（特征工程服务）

### 替换方案

**方案 1: 自定义 HTTP 服务**
- 如果特征服务是独立的 HTTP 服务
- 使用 RestTemplate 或 WebClient 调用

**方案 2: Feature Store**
- 使用开源的 Feature Store（如 Feast）
- 适合特征工程场景

**方案 3: Redis/数据库存储**
- 如果特征数据可以预计算
- 存储在 Redis 或数据库中

**建议**: 根据实际业务需求选择方案。

---

## 🟢 低优先级：Odin 监控

### 当前状态

- **文件**: `monitor/OdinMonitor.java`
- **状态**: 占位实现，仅缓存指标
- **用途**: 监控指标上报

### 替换方案

**方案 1: Spring Boot Actuator + Prometheus**（推荐）
- Spring Boot 原生支持
- 标准化的监控方案
- 已有 `StatsdClient`，可以复用

**方案 2: Micrometer**
- Spring Boot 官方推荐
- 支持多种监控后端（Prometheus、InfluxDB 等）

**方案 3: 继续使用 StatsD**
- 项目已有 `StatsdClient`
- 可以完全替代 Odin

**建议**: 使用 Spring Boot Actuator + Prometheus，项目已配置 Actuator。

---

## 📊 替换优先级建议

### 第一阶段（高优先级）

1. **Apollo → Nacos 配置中心**
   - 影响范围大（19 个文件）
   - 配置管理是核心功能
   - 可以复用现有的 Nacos 基础设施

### 第二阶段（中优先级）

2. **DirPC 清理或替换**
   - 如果未使用，直接删除
   - 如果使用，替换为 gRPC 或 Dubbo

3. **Dufe 特征服务**
   - 根据实际业务需求实现
   - 或使用 Feature Store

### 第三阶段（低优先级）

4. **Odin 监控**
   - 使用 Spring Boot Actuator
   - 或继续使用 StatsD

---

## 🔧 替换影响分析

### Apollo 替换影响

**影响文件**: 19 个文件
- `config/ApolloConfigService.java` - 核心配置服务
- `business/*` - 多个业务模块
- `llm/*` - LLM 相关配置
- `config/*` - 配置服务

**工作量**: 中等
- 需要创建 Nacos 配置服务适配层
- 需要迁移配置数据
- 需要更新所有调用点

### DirPC 替换影响

**影响文件**: 1 个文件
- `config/DirPCInitializer.java`

**工作量**: 低（如果未使用）
- 如果未使用，直接删除即可

### Dufe 替换影响

**影响文件**: 1 个文件
- `io/DufeClient.java`

**工作量**: 取决于业务需求
- 需要了解特征服务的实际需求
- 需要实现特征获取逻辑

### Odin 替换影响

**影响文件**: 1 个文件
- `monitor/OdinMonitor.java`

**工作量**: 低
- 已有 StatsD 和 Actuator
- 可以完全替代

---

## 📝 建议行动

1. **立即行动**: 
   - 删除或标记废弃 `DiSFInitializer`（已替换为 Nacos）

2. **短期计划**:
   - 替换 Apollo 为 Nacos 配置中心
   - 评估 DirPC 是否使用，未使用则删除

3. **中期计划**:
   - 实现 Dufe 特征服务（根据业务需求）
   - 完善监控方案（使用 Actuator）

4. **长期计划**:
   - 完全移除所有滴滴内部组件依赖
   - 使用标准化的开源组件

---

## 🔗 相关文档

- [Nacos 服务发现文档](src/main/java/com/wuxiansheng/shieldarch/marsdata/utils/README_NACOS_SERVICE_DISCOVERY.md)
- [Docker 部署文档](docker/README.md)

