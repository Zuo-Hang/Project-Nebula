# StatsD 到 Prometheus + Grafana 迁移方案

## 📋 迁移目标

将现有的 StatsD 监控方案迁移到 Prometheus + Grafana，实现：
- 更好的标签（Labels）支持
- 强大的查询能力（PromQL）
- 丰富的可视化界面（Grafana）
- 自动收集 Spring Boot 指标
- 云原生标准方案

## 🎯 迁移策略

### 渐进式迁移（推荐）

1. **阶段1：并行运行**（1-2周）
   - 同时支持 StatsD 和 Prometheus
   - 新功能使用 Prometheus
   - 旧功能保持 StatsD

2. **阶段2：逐步迁移**（2-4周）
   - 逐个模块迁移到 Prometheus
   - 验证指标一致性
   - 保留 StatsD 作为备份

3. **阶段3：完全切换**（1周）
   - 所有模块迁移完成
   - 移除 StatsD 依赖
   - 清理旧代码

## 📦 技术方案

### 1. 创建兼容层

创建 `PrometheusMetricsClient`，提供与 `StatsdClient` 相同的接口，内部使用 Micrometer：

```java
// 兼容现有代码，无需修改业务逻辑
statsdClient.recordRpcMetric("llm_req", caller, "llm", duration, code);
// ↓ 内部转换为
prometheusClient.recordRpcMetric("llm_req", caller, "llm", duration, code);
```

### 2. 配置切换

通过配置控制使用哪个监控系统：
```yaml
monitoring:
  type: prometheus  # 或 statsd
  prometheus:
    enabled: true
  statsd:
    enabled: false  # 迁移完成后设为 false
```

## 🔧 实施步骤

### Step 1: 添加依赖

在 `pom.xml` 中添加：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Step 2: 配置 Actuator

在 `application.yml` 中配置：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

### Step 3: 创建 PrometheusMetricsClient

创建兼容 `StatsdClient` 接口的 Prometheus 客户端。

### Step 4: 部署 Prometheus 和 Grafana

使用 Docker Compose 部署监控基础设施。

### Step 5: 逐步迁移代码

按模块迁移，从低风险模块开始。

## 📊 迁移清单

### 需要迁移的模块

- [ ] LLMClient - LLM 请求指标
- [ ] ReasonService - 缓存指标
- [ ] MQ Producer/Consumer - 消息队列指标
- [ ] Scheduler - 定时任务指标
- [ ] RedisWrapper - Redis 指标
- [ ] PoiService/QuestService - 外部服务指标
- [ ] Business Sinkers - 业务监控指标
- [ ] Business Posters - 业务过滤指标

### 迁移优先级

1. **高优先级**（核心业务）
   - LLMClient
   - ReasonService
   - MQ Producer/Consumer

2. **中优先级**（基础设施）
   - RedisWrapper
   - Scheduler
   - External Services

3. **低优先级**（业务监控）
   - Business Sinkers
   - Business Posters

## ✅ 验证清单

- [ ] Prometheus 能正常拉取指标
- [ ] Grafana 能正常显示数据
- [ ] 指标名称和标签正确
- [ ] 指标值与 StatsD 一致
- [ ] 告警规则正常工作
- [ ] 性能无影响

## 📚 参考文档

- [Micrometer 文档](https://micrometer.io/docs)
- [Prometheus 文档](https://prometheus.io/docs)
- [Grafana 文档](https://grafana.com/docs)

