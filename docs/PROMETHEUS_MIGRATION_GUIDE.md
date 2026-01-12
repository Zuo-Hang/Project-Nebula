# Prometheus 迁移指南

## 🚀 快速开始

### 1. 启动监控服务

```bash
cd docker
docker-compose up -d prometheus grafana
```

### 2. 访问服务

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - 用户名: `admin`
  - 密码: `admin`

### 3. 配置应用

在 `application.yml` 中设置：
```yaml
monitoring:
  type: both  # 同时使用 StatsD 和 Prometheus
```

### 4. 验证指标

访问应用的 Prometheus 端点：
```
http://localhost:8080/actuator/prometheus
```

## 📝 代码迁移示例

### 方式1：使用 MetricsClientAdapter（推荐）

```java
@Autowired
private MetricsClientAdapter metricsClient;

// 原有代码无需修改
metricsClient.recordRpcMetric("llm_req", caller, "llm", duration, code);
```

### 方式2：直接使用 PrometheusMetricsClient

```java
@Autowired
private PrometheusMetricsClient prometheusClient;

// 使用 Prometheus 客户端
prometheusClient.recordRpcMetric("llm_req", caller, "llm", duration, code);
```

### 方式3：逐步替换 StatsdClient

```java
// 旧代码
@Autowired
private StatsdClient statsdClient;

// 新代码（保持接口兼容）
@Autowired
private PrometheusMetricsClient prometheusClient;

// 使用方式相同
prometheusClient.recordRpcMetric("llm_req", caller, "llm", duration, code);
```

## 🔍 Prometheus 查询示例

### 查询 LLM 请求总数
```
llm_req_total
```

### 查询 LLM 请求成功率
```
rate(llm_req_total{status="success"}[5m]) / rate(llm_req_total[5m])
```

### 查询 LLM 请求平均耗时
```
rate(llm_req_duration_ms_sum[5m]) / rate(llm_req_duration_ms_count[5m])
```

### 按业务分组查询
```
sum by (business) (llm_req_total)
```

## 📊 Grafana 仪表盘

### 导入预置仪表盘

1. 登录 Grafana
2. 点击 "+" → "Import"
3. 输入 Dashboard ID 或上传 JSON 文件

### 创建自定义仪表盘

参考指标：
- `llm_req_total` - LLM 请求总数
- `llm_req_duration_ms` - LLM 请求耗时
- `llm_cache_hit_total` - 缓存命中数
- `mq_producer_total` - MQ 发送数
- `scheduler_task_total` - 定时任务执行数

## ⚙️ 配置说明

### 监控类型配置

```yaml
monitoring:
  type: both  # statsd | prometheus | both
```

- `statsd`: 仅使用 StatsD
- `prometheus`: 仅使用 Prometheus
- `both`: 同时使用（推荐迁移阶段使用）

### Prometheus 配置

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: llm-data-collect
      environment: prod
```

## 🔄 迁移步骤

### Step 1: 添加依赖（已完成）
已在 `pom.xml` 中添加 `micrometer-registry-prometheus`

### Step 2: 配置 Actuator（已完成）
已在 `application.yml` 中配置 Prometheus 端点

### Step 3: 部署监控服务（已完成）
已在 `docker-compose.yml` 中添加 Prometheus 和 Grafana

### Step 4: 逐步迁移代码

1. **低风险模块**（先迁移）
   - 新功能直接使用 `PrometheusMetricsClient`
   - 或使用 `MetricsClientAdapter`（自动双写）

2. **核心模块**（验证后迁移）
   - LLMClient
   - ReasonService
   - MQ Producer/Consumer

3. **业务模块**（最后迁移）
   - Business Sinkers
   - Business Posters

### Step 5: 验证和切换

1. 对比 StatsD 和 Prometheus 指标一致性
2. 验证 Grafana 仪表盘显示正常
3. 配置告警规则
4. 切换 `monitoring.type` 为 `prometheus`
5. 移除 StatsD 依赖（可选）

## 📈 监控指标映射

| StatsD 指标 | Prometheus 指标 | 说明 |
|------------|----------------|------|
| `llm_req` | `llm_req_total` | LLM 请求总数 |
| `llm_req` (timing) | `llm_req_duration_ms` | LLM 请求耗时 |
| `llm_req_success` | `llm_req_total{status="success"}` | 成功请求数 |
| `llm_req_fail` | `llm_req_total{status="fail"}` | 失败请求数 |
| `llm_cache_hit` | `llm_cache_hit_total` | 缓存命中数 |
| `llm_cache_miss` | `llm_cache_miss_total` | 缓存未命中数 |

## 🎯 最佳实践

1. **指标命名**
   - 使用 `_total` 后缀表示 Counter
   - 使用 `_duration_ms` 表示 Timer
   - 使用 `_bytes` 表示大小

2. **标签使用**
   - 使用标签区分不同维度（业务、环境等）
   - 避免高基数标签（如用户ID）

3. **指标聚合**
   - 在应用层聚合，减少指标数量
   - 使用 PromQL 在查询时聚合

4. **性能考虑**
   - Prometheus 使用拉取模式，对应用性能影响小
   - 指标缓存避免重复创建 Meter

## 🐛 故障排查

### Prometheus 无法拉取指标

1. 检查应用是否启动
2. 检查 `/actuator/prometheus` 端点是否可访问
3. 检查 Prometheus 配置中的 targets 是否正确

### Grafana 无法显示数据

1. 检查 Prometheus 数据源配置
2. 检查时间范围设置
3. 检查 PromQL 查询语法

### 指标不一致

1. 检查指标名称是否匹配
2. 检查标签是否正确
3. 检查时间窗口是否一致

## 📚 参考资源

- [Micrometer 文档](https://micrometer.io/docs)
- [Prometheus 文档](https://prometheus.io/docs)
- [Grafana 文档](https://grafana.com/docs)
- [PromQL 查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)

