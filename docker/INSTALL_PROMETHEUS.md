# Prometheus + Grafana 安装指南

## 📋 前置要求

- Docker 已安装并运行
- Docker Compose 已安装（或 Docker 内置的 `docker compose`）

## 🚀 快速安装

### 方式1: 使用启动脚本（推荐）

```bash
cd docker
./start.sh
```

这会启动所有服务，包括 Prometheus 和 Grafana。

### 方式2: 仅启动监控服务

```bash
cd docker
docker-compose up -d prometheus grafana
```

### 方式3: 手动启动

```bash
cd docker

# 启动 Prometheus
docker-compose up -d prometheus

# 启动 Grafana
docker-compose up -d grafana
```

## ✅ 验证安装

### 1. 检查容器状态

```bash
cd docker
docker-compose ps prometheus grafana
```

应该看到两个容器的状态都是 `Up`。

### 2. 检查 Prometheus

访问 http://localhost:9090

- 如果看到 Prometheus 界面，说明安装成功
- 点击 "Status" → "Targets" 查看抓取目标状态

### 3. 检查 Grafana

访问 http://localhost:3000

- 使用 `admin` / `admin` 登录
- 如果看到 Grafana 首页，说明安装成功

### 4. 检查应用指标

```bash
# 确保应用已启动
curl http://localhost:8080/actuator/prometheus
```

如果返回指标数据，说明应用指标端点正常。

## 🔧 配置说明

### Prometheus 配置

配置文件位置：`docker/prometheus/prometheus.yml`

主要配置项：
- `scrape_interval`: 抓取间隔（15秒）
- `scrape_configs`: 抓取目标列表
  - `llm-data-collect`: 你的 Spring Boot 应用

**注意**：如果应用运行在宿主机上，Prometheus 在 Docker 容器内需要使用 `host.docker.internal` 访问。

### Grafana 配置

- **数据源**：自动配置在 `docker/grafana/provisioning/datasources/prometheus.yml`
- **仪表盘**：从 `docker/grafana/provisioning/dashboards/` 目录自动加载

## 🐛 故障排查

### 问题1: 容器无法启动

**检查 Docker 是否运行**
```bash
docker info
```

**查看容器日志**
```bash
docker-compose logs prometheus
docker-compose logs grafana
```

### 问题2: Prometheus 无法访问应用

**检查应用是否运行**
```bash
curl http://localhost:8080/actuator/prometheus
```

**检查 Prometheus 配置**
- 确认 `targets` 中的地址正确
- 从 Docker 内部访问宿主机使用 `host.docker.internal:8080`

**修改配置后重新加载**
```bash
# 方式1: 重启容器
docker-compose restart prometheus

# 方式2: 使用 Prometheus 的 reload API（如果启用了 lifecycle）
curl -X POST http://localhost:9090/-/reload
```

### 问题3: Grafana 无法连接 Prometheus

**检查 Prometheus 是否运行**
```bash
curl http://localhost:9090/-/healthy
```

**检查网络连接**
- 确认 Prometheus 和 Grafana 在同一个 Docker 网络中
- 在 Grafana 中，Prometheus 的地址应该是 `http://prometheus:9090`（容器名）

**手动配置数据源**
1. 登录 Grafana
2. 进入 Configuration → Data Sources
3. 添加 Prometheus 数据源
4. URL: `http://prometheus:9090`
5. 点击 "Save & Test"

### 问题4: 端口冲突

如果 9090 或 3000 端口被占用：

**修改 docker-compose.yml**
```yaml
prometheus:
  ports:
    - "9091:9090"  # 改为其他端口

grafana:
  ports:
    - "3001:3000"  # 改为其他端口
```

## 📊 常用命令

### 启动服务
```bash
docker-compose up -d prometheus grafana
```

### 停止服务
```bash
docker-compose stop prometheus grafana
```

### 重启服务
```bash
docker-compose restart prometheus grafana
```

### 查看日志
```bash
# 查看所有日志
docker-compose logs -f prometheus grafana

# 查看 Prometheus 日志
docker-compose logs -f prometheus

# 查看 Grafana 日志
docker-compose logs -f grafana
```

### 查看状态
```bash
docker-compose ps prometheus grafana
```

### 进入容器
```bash
# 进入 Prometheus 容器
docker exec -it prometheus sh

# 进入 Grafana 容器
docker exec -it grafana sh
```

### 删除容器和数据
```bash
# 停止并删除容器
docker-compose down prometheus grafana

# 删除容器和数据卷（会丢失所有历史数据）
docker-compose down -v prometheus grafana
```

## 🔄 更新配置

### 更新 Prometheus 配置

1. 编辑 `docker/prometheus/prometheus.yml`
2. 重新加载配置：
   ```bash
   docker-compose restart prometheus
   # 或使用 reload API（如果启用了）
   curl -X POST http://localhost:9090/-/reload
   ```

### 更新 Grafana 配置

1. 编辑 Grafana 配置文件
2. 重启 Grafana：
   ```bash
   docker-compose restart grafana
   ```

## 📚 下一步

安装完成后，参考以下文档：
- [Prometheus 迁移指南](../docs/PROMETHEUS_MIGRATION_GUIDE.md)
- [Prometheus 使用说明](README_PROMETHEUS.md)

