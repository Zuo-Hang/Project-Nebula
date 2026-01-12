# Prometheus 迁移代码示例

## 📝 迁移示例

### 示例1: LLMClient 迁移

#### 原代码（使用 StatsdClient）
```java
@Autowired
private StatsdClient statsdClient;

public void requestLLM(...) {
    long beginTime = System.currentTimeMillis();
    try {
        // ... 业务逻辑
    } finally {
        if (statsdClient != null) {
            long duration = System.currentTimeMillis() - beginTime;
            statsdClient.recordRpcMetric("llm_req", request.getCaller(), "llm", 
                duration, error == null ? 0 : 1);
        }
    }
}
```

#### 迁移后（使用 MetricsClientAdapter - 推荐）
```java
@Autowired
private MetricsClientAdapter metricsClient;  // 自动支持双写

public void requestLLM(...) {
    long beginTime = System.currentTimeMillis();
    try {
        // ... 业务逻辑
    } finally {
        if (metricsClient != null) {
            long duration = System.currentTimeMillis() - beginTime;
            metricsClient.recordRpcMetric("llm_req", request.getCaller(), "llm", 
                duration, error == null ? 0 : 1);
        }
    }
}
```

#### 迁移后（直接使用 PrometheusMetricsClient）
```java
@Autowired
private PrometheusMetricsClient prometheusClient;

public void requestLLM(...) {
    long beginTime = System.currentTimeMillis();
    try {
        // ... 业务逻辑
    } finally {
        if (prometheusClient != null && prometheusClient.isEnabled()) {
            long duration = System.currentTimeMillis() - beginTime;
            prometheusClient.recordRpcMetric("llm_req", request.getCaller(), "llm", 
                duration, error == null ? 0 : 1);
        }
    }
}
```

### 示例2: ReasonService 缓存指标迁移

#### 原代码
```java
@Autowired
private StatsdClient statsdClient;

if (statsdClient != null) {
    statsdClient.incrementCounter("llm_cache_hit", Map.of("business", businessName));
}
```

#### 迁移后
```java
@Autowired
private MetricsClientAdapter metricsClient;

if (metricsClient != null) {
    metricsClient.incrementCounter("llm_cache_hit", Map.of("business", businessName));
}
```

### 示例3: MQ Producer 指标迁移

#### 原代码
```java
@Autowired
private StatsDUtils statsDUtils;

if (statsDUtils != null) {
    statsDUtils.counter("ddmq_producer", Map.of("topic", topic));
}
```

#### 迁移后
```java
@Autowired
private MetricsClientAdapter metricsClient;

if (metricsClient != null) {
    metricsClient.incrementCounter("ddmq_producer", Map.of("topic", topic));
}
```

## 🔄 迁移步骤

### Step 1: 更新依赖注入

将 `StatsdClient` 替换为 `MetricsClientAdapter`：

```java
// 旧代码
@Autowired
private StatsdClient statsdClient;

// 新代码
@Autowired
private MetricsClientAdapter metricsClient;
```

### Step 2: 更新方法调用

方法签名保持不变，直接替换对象：

```java
// 旧代码
statsdClient.recordRpcMetric(...);

// 新代码
metricsClient.recordRpcMetric(...);
```

### Step 3: 验证指标

1. 启动应用
2. 访问 http://localhost:8080/actuator/prometheus
3. 检查指标是否正确上报
4. 在 Prometheus 中查询验证

## 📊 指标映射表

| StatsD 方法 | Prometheus 方法 | 说明 |
|------------|----------------|------|
| `increment()` | `increment()` | 累加计数 |
| `count()` | `count()` | 计数（带值） |
| `timing()` | `timing()` | 记录耗时 |
| `recordRpcMetric()` | `recordRpcMetric()` | RPC 指标 |
| `recordGauge()` | `recordGauge()` | 瞬时值 |
| `incrementCounter()` | `incrementCounter()` | 累加计数 |

## ✅ 迁移检查清单

- [ ] 替换所有 `StatsdClient` 为 `MetricsClientAdapter`
- [ ] 验证方法调用参数一致
- [ ] 检查指标名称是否符合 Prometheus 规范
- [ ] 验证标签（tags）是否正确传递
- [ ] 在 Prometheus 中验证指标存在
- [ ] 在 Grafana 中创建仪表盘
- [ ] 对比 StatsD 和 Prometheus 指标值
- [ ] 配置告警规则

## 🎯 最佳实践

1. **指标命名**
   - 使用小写字母和下划线
   - Counter 使用 `_total` 后缀
   - Timer 使用 `_duration_ms` 后缀

2. **标签使用**
   - 使用有意义的标签（business, status, method 等）
   - 避免高基数标签（如用户ID、订单ID）

3. **性能优化**
   - MetricsClientAdapter 会自动缓存 Meter 实例
   - 避免频繁创建相同指标的 Meter

4. **渐进式迁移**
   - 使用 `monitoring.type: both` 同时运行
   - 验证无误后切换到 `prometheus`
   - 最后移除 StatsD 依赖

