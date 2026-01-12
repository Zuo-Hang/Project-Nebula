# Prometheus + Grafana 监控服务

## 🚀 快速启动

```bash
# 启动 Prometheus 和 Grafana
cd docker
docker-compose up -d prometheus grafana

# 查看日志
docker-compose logs -f prometheus grafana
```

## 📊 访问地址

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - 用户名: `admin`
  - 密码: `admin`

## 🔧 配置说明

### Prometheus 配置

配置文件：`docker/prometheus/prometheus.yml`

主要配置：
- `scrape_interval`: 抓取间隔（15秒）
- `scrape_configs`: 抓取目标配置
  - `llm-data-collect`: Spring Boot 应用指标
  - `prometheus`: Prometheus 自身指标

### Grafana 配置

- **数据源**: 自动配置 Prometheus（`docker/grafana/provisioning/datasources/`）
- **仪表盘**: 从 `docker/grafana/dashboards/` 目录加载

## 📈 验证指标

### 1. 检查应用指标端点

```bash
curl http://localhost:8080/actuator/prometheus
```

### 2. 在 Prometheus 中查询

访问 http://localhost:9090，在查询框中输入：
```
llm_req_total
```

### 3. 在 Grafana 中查看

1. 登录 Grafana
2. 创建新仪表盘
3. 添加 Panel
4. 选择 Prometheus 数据源
5. 输入 PromQL 查询

## 🔍 常用 PromQL 查询

### LLM 请求总数
```
llm_req_total
```

### LLM 请求速率（每秒）
```
rate(llm_req_total[5m])
```

### LLM 请求成功率
```
sum(rate(llm_req_total{status="success"}[5m])) / sum(rate(llm_req_total[5m]))
```

### LLM 请求平均耗时（毫秒）
```
rate(llm_req_duration_ms_sum[5m]) / rate(llm_req_duration_ms_count[5m])
```

### 按业务分组统计
```
sum by (business) (llm_req_total)
```

## 🛠️ 故障排查

### Prometheus 无法拉取指标

1. **检查应用是否运行**
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```

2. **检查 Prometheus 配置**
   - 确认 `targets` 中的地址正确
   - 从 Docker 内部访问宿主机使用 `host.docker.internal`

3. **查看 Prometheus 日志**
   ```bash
   docker-compose logs prometheus
   ```

4. **检查 Prometheus Targets**
   - 访问 http://localhost:9090/targets
   - 查看目标状态是否为 "UP"

### Grafana 无法显示数据

1. **检查数据源连接**
   - 登录 Grafana
   - 进入 Configuration → Data Sources
   - 测试 Prometheus 连接

2. **检查时间范围**
   - 确认时间范围设置正确
   - 尝试扩大时间范围

3. **检查 PromQL 语法**
   - 在 Prometheus 中先验证查询
   - 确认指标名称正确

## 📝 数据保留

Prometheus 默认保留 30 天数据，可在 `prometheus.yml` 中配置：
```yaml
storage:
  tsdb:
    retention.time: 30d
```

## 🔄 重启服务

```bash
# 重启 Prometheus
docker-compose restart prometheus

# 重启 Grafana
docker-compose restart grafana

# 重启所有监控服务
docker-compose restart prometheus grafana
```

## 🗑️ 清理数据

```bash
# 停止服务
docker-compose stop prometheus grafana

# 删除数据卷（会丢失所有历史数据）
docker-compose down -v

# 重新启动
docker-compose up -d prometheus grafana
```

## 📚 相关文档

- [Prometheus 官方文档](https://prometheus.io/docs)
- [Grafana 官方文档](https://grafana.com/docs)
- [迁移指南](../docs/PROMETHEUS_MIGRATION_GUIDE.md)

