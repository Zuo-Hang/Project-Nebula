# 监控告警遗漏检查报告

## ✅ 已配置项

### 1. Prometheus 告警规则
- ✅ 10 个告警规则已配置
- ✅ 告警规则文件已引用

### 2. Grafana 仪表盘
- ✅ 5 个仪表盘已创建
- ✅ 自动加载配置已设置

### 3. 指标上报
- ✅ 所有代码已迁移到 MetricsClientAdapter
- ✅ Prometheus 端点已暴露

---

## ❌ 发现的遗漏项

### 1. AlertManager 缺失 🔴 高优先级

**问题：**
- 告警规则已配置，但没有 AlertManager 来处理告警通知
- 告警只能在 Prometheus UI 中查看，无法发送通知

**影响：**
- 告警无法及时通知到相关人员
- 需要人工查看 Prometheus UI

**解决方案：**
需要在 `docker-compose.yml` 中添加 AlertManager 服务

---

### 2. 告警规则中的指标名称可能不匹配 🟡 中优先级

**问题：**
根据 `PrometheusMetricsClient` 的实现：
- `recordRpcMetric` 创建的指标是：`{method}_duration_ms` 和 `{method}_total`
- 但告警规则中使用的是 `llm_req_duration_ms_bucket`（需要确认是否有 bucket）

**需要检查的指标：**
1. `llm_req_total` - ✅ 正确（来自 `recordRpcMetric("llm_req", ...)`）
2. `llm_req_duration_ms_bucket` - ⚠️ 需要确认（Timer 会自动创建 bucket）
3. `scheduler_task_total` - ⚠️ 需要确认是否有 `status` 标签
4. `scheduler_task_duration` - ⚠️ 需要确认实际指标名称

---

### 3. Prometheus 配置中缺少告警规则文件挂载 🟡 中优先级

**问题：**
`docker-compose.yml` 中 Prometheus 只挂载了 `prometheus.yml`，没有挂载 `alert_rules.yml`

**当前配置：**
```yaml
volumes:
  - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
```

**应该改为：**
```yaml
volumes:
  - ./prometheus:/etc/prometheus:ro
```

---

### 4. 缺少 JVM 相关告警 🟡 中优先级

**问题：**
- 告警规则中有 JVM 内存告警，但需要确认 Spring Boot Actuator 是否暴露了这些指标
- 缺少 JVM GC 相关告警
- 缺少线程数告警

---

### 5. 缺少业务指标告警 🟡 中优先级

**问题：**
- 缺少消息延迟告警（`msg_latency_s`）
- 缺少过期消息告警（`expire_msg_counter`）
- 缺少业务监控指标告警（如 `b_saas_monitor`、`sinker_monitor` 等）

---

### 6. 缺少外部服务告警 🟢 低优先级

**问题：**
- 缺少 MapAPI 调用失败告警
- 缺少 Quest 服务调用失败告警

---

### 7. 缺少数据质量告警 🟡 中优先级

**问题：**
- 缺少数据完整性告警（`integrity_missing_count`）
- 缺少价格拟合异常告警（`price_fitting_missing_response_rate`）

---

## 🔧 需要修复的问题

### 优先级1：必须修复

1. **添加 AlertManager 到 docker-compose.yml**
2. **修复 Prometheus 配置，挂载整个 prometheus 目录**

### 优先级2：建议修复

3. **验证并修复告警规则中的指标名称**
4. **添加业务指标告警**
5. **添加数据质量告警**

### 优先级3：可选

6. **添加 JVM GC 告警**
7. **添加外部服务告警**

---

## 📋 修复清单

- [ ] 添加 AlertManager 服务到 docker-compose.yml
- [ ] 创建 AlertManager 配置文件
- [ ] 修复 Prometheus 配置，挂载整个目录
- [ ] 验证告警规则中的指标名称
- [ ] 添加消息延迟告警
- [ ] 添加数据质量告警
- [ ] 添加 JVM GC 告警（可选）
- [ ] 添加外部服务告警（可选）

