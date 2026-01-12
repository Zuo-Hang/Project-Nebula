# Prometheus + Grafana 快速安装

## 🚀 一键启动（最简单）

```bash
cd docker
./start.sh
```

这会启动所有服务，包括 Prometheus 和 Grafana。

## 📦 仅安装监控服务

如果你想只启动 Prometheus 和 Grafana：

```bash
cd docker
docker-compose up -d prometheus grafana
```

## ✅ 验证安装

### 1. 检查服务状态

```bash
docker-compose ps prometheus grafana
```

应该看到两个容器都是 `Up` 状态。

### 2. 访问服务

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - 用户名: `admin`
  - 密码: `admin`

### 3. 验证应用指标

确保你的 Spring Boot 应用已启动，然后访问：
```
http://localhost:8080/actuator/prometheus
```

如果能看到指标数据，说明一切正常！

## 🔍 在 Prometheus 中查看指标

1. 访问 http://localhost:9090
2. 在查询框中输入：`llm_req_total`
3. 点击 "Execute"
4. 如果看到数据，说明 Prometheus 正在收集指标

## 📊 在 Grafana 中创建仪表盘

1. 访问 http://localhost:3000
2. 登录（admin/admin）
3. 点击 "+" → "Create Dashboard"
4. 添加 Panel
5. 选择 Prometheus 数据源
6. 输入 PromQL 查询，如：`llm_req_total`

## 🛠️ 常用命令

```bash
# 启动
docker-compose up -d prometheus grafana

# 停止
docker-compose stop prometheus grafana

# 重启
docker-compose restart prometheus grafana

# 查看日志
docker-compose logs -f prometheus grafana

# 查看状态
docker-compose ps prometheus grafana
```

## ❓ 遇到问题？

查看详细文档：[INSTALL_PROMETHEUS.md](INSTALL_PROMETHEUS.md)

