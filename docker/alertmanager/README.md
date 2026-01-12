# AlertManager 配置说明

## 📋 功能

AlertManager 负责接收 Prometheus 的告警，并进行：
- **告警分组**：将相同类型的告警分组
- **告警抑制**：避免重复告警
- **告警路由**：根据标签路由到不同的接收者
- **告警通知**：发送到邮件、Webhook、钉钉等

## 🚀 使用方式

### 1. 启动服务

AlertManager 已添加到 `docker-compose.yml`，启动所有服务即可：

```bash
cd docker
./start.sh
```

### 2. 访问 AlertManager UI

- 地址：http://localhost:9093
- 可以查看：
  - **Alerts**：当前活跃的告警
  - **Silences**：已静默的告警
  - **Status**：AlertManager 状态

## 📧 配置通知渠道

### 方式1：邮件通知

编辑 `alertmanager.yml`，取消注释并配置：

```yaml
email_configs:
  - to: 'ops-team@example.com'
    from: 'alertmanager@example.com'
    smarthost: 'smtp.example.com:587'
    auth_username: 'alertmanager@example.com'
    auth_password: 'password'
```

### 方式2：Webhook 通知（钉钉/企业微信）

#### 钉钉机器人

1. 在钉钉群中添加自定义机器人
2. 获取 Webhook URL
3. 配置 `alertmanager.yml`：

```yaml
webhook_configs:
  - url: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
    send_resolved: true
```

#### 企业微信

```yaml
webhook_configs:
  - url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=YOUR_KEY'
    send_resolved: true
```

### 方式3：自定义 Webhook

可以开发一个 Webhook 服务接收告警，然后转发到任意系统。

## 🔧 告警路由规则

当前配置的路由规则：

1. **严重告警** (`severity: critical`)
   - 路由到 `critical-receiver`
   - 例如：应用宕机、定时任务失败

2. **警告告警** (`severity: warning`)
   - 路由到 `warning-receiver`
   - 例如：错误率过高、延迟过高

3. **默认接收者**
   - 其他告警路由到 `default-receiver`

## 🔕 告警抑制规则

当前配置的抑制规则：

- 如果应用宕机（`ApplicationDown`），会抑制其他警告级别的告警
- 避免在应用宕机时产生大量告警噪音

## 📝 自定义告警消息

可以在 `alertmanager.yml` 中配置模板文件来自定义告警消息格式：

```yaml
templates:
  - '/etc/alertmanager/templates/*.tmpl'
```

然后在 `templates/` 目录创建模板文件。

## 🧪 测试告警

### 方式1：在 Prometheus 中手动触发

1. 访问 Prometheus UI：http://localhost:9090
2. 进入 **Alerts** 页面
3. 找到要测试的告警规则
4. 点击告警名称，查看详情

### 方式2：使用 AlertManager API

```bash
# 发送测试告警
curl -X POST http://localhost:9093/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '[{
    "labels": {
      "alertname": "TestAlert",
      "severity": "warning"
    },
    "annotations": {
      "summary": "测试告警",
      "description": "这是一个测试告警"
    }
  }]'
```

## 🔍 故障排查

### 告警未发送

1. **检查 AlertManager 日志**
   ```bash
   docker logs alertmanager
   ```

2. **检查 Prometheus 配置**
   - 确认 `prometheus.yml` 中 `alerting.alertmanagers` 配置正确
   - 确认 AlertManager 服务名称是 `alertmanager`

3. **检查告警规则**
   - 在 Prometheus UI 中查看告警规则状态
   - 确认告警规则表达式正确

### 告警重复发送

1. **调整分组规则**
   - 修改 `group_by` 字段
   - 调整 `group_wait` 和 `group_interval`

2. **添加抑制规则**
   - 在 `inhibit_rules` 中添加规则

## 📚 参考文档

- [AlertManager 官方文档](https://prometheus.io/docs/alerting/latest/alertmanager/)
- [告警配置最佳实践](https://prometheus.io/docs/practices/alerting/)

