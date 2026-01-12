# Grafana 仪表盘说明

## 📊 已创建的仪表盘

1. **LLM 请求监控** (`llm-request-monitoring.json`)
   - LLM 请求 QPS
   - LLM 请求成功率
   - LLM 请求耗时 (P50/P95/P99)
   - LLM 请求错误率趋势

2. **缓存监控** (`cache-monitoring.json`)
   - 缓存命中率
   - 缓存未命中率
   - 缓存错误率
   - 缓存操作统计

3. **MQ 监控** (`mq-monitoring.json`)
   - MQ 发送速率
   - MQ 消费速率
   - MQ 消费延迟 (P95)
   - MQ 消费重试次数

4. **业务监控** (`business-monitoring.json`)
   - 各业务处理量
   - 业务处理成功率
   - 业务处理耗时 (P95)
   - Poster/Sinker 处理统计

5. **基础设施监控** (`infrastructure-monitoring.json`)
   - Redis 操作 QPS
   - Redis 操作延迟 (P95)
   - 外部服务调用 (MapAPI/Quest)
   - 定时任务执行统计

## 🚀 使用方式

### 方式1：自动加载（推荐）

仪表盘文件已放在 `docker/grafana/dashboards/` 目录，Grafana 会自动加载。

启动 Docker Compose 后，访问 Grafana：
- 地址：http://localhost:3000
- 用户名：admin
- 密码：admin

在 Grafana 左侧菜单选择 **Dashboards** → **Browse**，即可看到所有仪表盘。

### 方式2：手动导入

如果自动加载不工作，可以手动导入：

1. 登录 Grafana
2. 点击左侧菜单 **+** → **Import**
3. 点击 **Upload JSON file**
4. 选择对应的 JSON 文件
5. 点击 **Load**
6. 选择数据源（Prometheus）
7. 点击 **Import**

## 📝 注意事项

1. **指标名称**：这些仪表盘使用的指标名称基于 `PrometheusMetricsClient` 的实现
   - Counter 指标：`{metric}_total`
   - Timer 指标：`{metric}_duration_ms` 和 `{metric}_duration_ms_bucket`
   - Gauge 指标：`{metric}`

2. **标签**：指标可能包含以下标签
   - `business`: 业务名称
   - `status`: 状态（success/fail）
   - `method`: 方法名
   - `source`: 源唯一ID
   - `topic`: MQ 主题
   - `task`: 任务名称

3. **首次使用**：如果指标还没有数据，图表可能显示为空，这是正常的。等待应用产生指标数据后即可看到图表。

4. **自定义**：可以根据实际需求在 Grafana UI 中编辑这些仪表盘。

## 🔧 故障排查

如果仪表盘无法显示数据：

1. **检查 Prometheus 数据源**
   - 进入 Grafana → Configuration → Data Sources
   - 确认 Prometheus 数据源配置正确
   - 点击 **Test** 按钮测试连接

2. **检查指标是否存在**
   - 访问 Prometheus：http://localhost:9090
   - 在查询框中输入指标名称，如 `llm_req_total`
   - 确认指标有数据

3. **检查时间范围**
   - 确认 Grafana 右上角的时间范围设置正确
   - 建议使用 "Last 5 minutes" 或 "Last 1 hour"

4. **检查指标名称**
   - 访问应用指标端点：http://localhost:8080/actuator/prometheus
   - 查看实际暴露的指标名称
   - 如果与仪表盘中的不一致，需要更新仪表盘

