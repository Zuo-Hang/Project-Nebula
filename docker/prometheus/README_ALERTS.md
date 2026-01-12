# Prometheus 告警规则说明

## 📋 已配置的告警规则

### 1. LLM 相关告警

- **HighLLMErrorRate**: LLM 请求失败率超过 5%
- **HighLLMLatency**: LLM 请求 P95 延迟超过 5 秒

### 2. 缓存相关告警

- **LowCacheHitRate**: 缓存命中率低于 70%

### 3. MQ 相关告警

- **HighMQConsumeLatency**: MQ 消费 P95 延迟超过 10 秒
- **HighMQRetryRate**: MQ 消费重试率超过 10%

### 4. 业务相关告警

- **HighBusinessErrorRate**: 业务处理错误率超过 3%

### 5. 基础设施告警

- **HighRedisErrorRate**: Redis 操作失败率超过 5%
- **SchedulerTaskFailure**: 定时任务执行失败
- **ApplicationDown**: 应用实例宕机
- **HighJVMMemoryUsage**: JVM 堆内存使用率超过 85%

## 🚀 使用方式

### 1. 启用告警规则

告警规则文件 `alert_rules.yml` 已在 `prometheus.yml` 中引用：

```yaml
rule_files:
  - "alert_rules.yml"
```

重启 Prometheus 后，告警规则会自动加载。

### 2. 查看告警

访问 Prometheus UI：
- 地址：http://localhost:9090
- 点击顶部菜单 **Alerts**
- 可以看到所有告警规则的状态

### 3. 配置告警通知（可选）

如果需要配置告警通知（邮件、钉钉、企业微信等），需要：

1. **安装 AlertManager**
   ```yaml
   # 在 docker-compose.yml 中添加
   alertmanager:
     image: prom/alertmanager:latest
     ports:
       - "9093:9093"
   ```

2. **配置 AlertManager**
   - 创建 `docker/alertmanager/alertmanager.yml`
   - 配置通知渠道（邮件、Webhook 等）

3. **更新 Prometheus 配置**
   ```yaml
   # 在 prometheus.yml 中添加
   alerting:
     alertmanagers:
       - static_configs:
           - targets:
               - alertmanager:9093
   ```

## 📝 告警规则说明

### 告警级别

- **warning**: 警告级别，需要关注但不紧急
- **critical**: 严重级别，需要立即处理

### 告警持续时间

- 大部分告警需要持续一定时间（如 5 分钟）才会触发
- 这可以避免偶发的短暂异常触发告警

### 自定义告警阈值

可以根据实际需求修改 `alert_rules.yml` 中的阈值：

```yaml
# 例如，修改 LLM 错误率阈值从 5% 改为 3%
expr: |
  rate(llm_req_total{status="fail"}[5m]) / 
  rate(llm_req_total[5m]) > 0.03  # 改为 0.03
```

## 🔧 故障排查

### 告警规则未生效

1. **检查 Prometheus 配置**
   - 确认 `prometheus.yml` 中 `rule_files` 已启用
   - 确认文件路径正确

2. **检查 Prometheus 日志**
   ```bash
   docker logs prometheus
   ```
   查看是否有告警规则加载错误

3. **检查告警规则语法**
   - 访问 Prometheus UI → Alerts
   - 查看告警规则状态
   - 如果有错误，会显示错误信息

### 告警未触发

1. **检查指标是否存在**
   - 在 Prometheus 中查询告警规则使用的指标
   - 确认指标有数据

2. **检查告警条件**
   - 在 Prometheus 中直接执行告警规则的表达式
   - 查看结果是否符合预期

3. **检查时间范围**
   - 确认告警规则的 `for` 时间设置合理
   - 确认指标数据的时间范围足够

