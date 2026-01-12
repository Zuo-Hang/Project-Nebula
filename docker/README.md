# Docker 第三方组件部署说明

本目录包含项目所需的所有第三方组件的 Docker 配置，包括 Redis、RocketMQ、Nacos 等。

## 📋 包含的组件

- **Nacos** (v2.3.0) - 服务发现和配置管理
- **MySQL** (8.0) - Nacos 数据存储
- **Redis** (7.2) - 缓存和分布式锁
- **RocketMQ** (5.2.0) - 消息队列
  - NameServer
  - Broker
  - Console（管理控制台）

## 🚀 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+ 或 docker-compose 1.29+

### 启动所有服务

```bash
# 方式 1: 使用启动脚本（推荐）
cd docker
chmod +x start.sh
./start.sh

# 方式 2: 使用 Docker Compose 命令
cd docker
docker-compose up -d
# 或
docker compose up -d
```

### 停止所有服务

```bash
# 方式 1: 使用停止脚本
cd docker
chmod +x stop.sh
./stop.sh

# 方式 2: 使用 Docker Compose 命令
cd docker
docker-compose down
```

## 📊 服务访问地址

| 服务 | 地址 | 用户名/密码 | 说明 |
|------|------|------------|------|
| **Nacos 控制台** | http://localhost:8848/nacos | nacos/nacos | 服务发现和配置管理 |
| **RocketMQ 控制台** | http://localhost:8081 | - | 消息队列管理 |
| **Redis** | localhost:6379 | redis123456 | 缓存服务 |
| **MySQL (Nacos)** | localhost:3307 | root/root123456 | Nacos 数据存储 |

## 🔧 配置说明

### Nacos 配置

```yaml
# application.yml
nacos:
  enabled: true
  server-addr: 127.0.0.1:8848
  namespace:  # 可选
  username: nacos
  password: nacos
```

### Redis 配置

```yaml
# application.yml
spring:
  data:
    redisson:
      config: |
        singleServerConfig:
          address: redis://127.0.0.1:6379
          password: redis123456
```

### RocketMQ 配置

```yaml
# application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: llm-data-collect-producer-group
```

## 📁 目录结构

```
docker/
├── docker-compose.yml      # Docker Compose 配置文件
├── start.sh                # 启动脚本
├── stop.sh                 # 停止脚本
├── README.md               # 本文件
├── mysql/
│   └── init.sql            # MySQL 初始化脚本
├── redis/
│   └── redis.conf          # Redis 配置文件
└── rocketmq/
    └── broker.conf         # RocketMQ Broker 配置
```

## 🔍 常用命令

### 查看服务状态

```bash
cd docker
docker-compose ps
```

### 查看服务日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f nacos
docker-compose logs -f redis
docker-compose logs -f rocketmq-broker
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart nacos
```

### 进入容器

```bash
# 进入 Redis 容器
docker exec -it mars-data-redis sh

# 进入 Nacos 容器
docker exec -it nacos-server bash

# 进入 MySQL 容器
docker exec -it nacos-mysql bash
```

### 清理数据（谨慎使用）

```bash
# 停止并删除容器和数据卷
docker-compose down -v

# 只删除数据卷
docker volume rm docker_nacos-data docker_redis-data docker_mysql-data
```

## 🔐 默认密码

| 服务 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| Nacos | nacos | nacos | 建议在生产环境修改 |
| MySQL | root | root123456 | 仅用于 Nacos 数据存储 |
| MySQL | nacos | nacos | Nacos 数据库用户 |
| Redis | - | redis123456 | Redis 密码 |

⚠️ **生产环境请务必修改所有默认密码！**

## 🐛 故障排查

### 服务无法启动

1. 检查端口是否被占用：
```bash
# 检查端口占用
netstat -an | grep 8848  # Nacos
netstat -an | grep 6379  # Redis
netstat -an | grep 9876  # RocketMQ
```

2. 查看服务日志：
```bash
docker-compose logs [服务名]
```

3. 检查 Docker 资源：
```bash
docker system df
docker system prune  # 清理未使用的资源
```

### Nacos 无法访问

1. 检查 Nacos 是否启动：
```bash
docker-compose ps nacos
```

2. 检查 Nacos 日志：
```bash
docker-compose logs nacos
```

3. 检查 MySQL 连接：
```bash
docker-compose logs mysql
```

### RocketMQ 消息发送失败

1. 检查 NameServer 和 Broker 是否都启动：
```bash
docker-compose ps | grep rocketmq
```

2. 检查 Broker 配置：
```bash
docker exec -it rocketmq-broker cat /home/rocketmq/rocketmq-5.2.0/conf/broker.conf
```

### Redis 连接失败

1. 检查 Redis 是否启动：
```bash
docker-compose ps redis
```

2. 测试 Redis 连接：
```bash
docker exec -it mars-data-redis redis-cli -a redis123456 ping
```

## 📝 环境变量

可以通过环境变量覆盖默认配置：

```bash
# 设置 Redis 密码
export REDIS_PASSWORD=your_password

# 设置 MySQL root 密码
export MYSQL_ROOT_PASSWORD=your_password

# 启动服务
docker-compose up -d
```

## 🔄 数据持久化

所有数据都存储在 Docker 数据卷中：

- `nacos-data`: Nacos 数据
- `nacos-logs`: Nacos 日志
- `mysql-data`: MySQL 数据
- `redis-data`: Redis 数据
- `rocketmq-*-logs`: RocketMQ 日志
- `rocketmq-broker-store`: RocketMQ 消息存储

数据卷在 `docker-compose down` 时不会删除，除非使用 `-v` 参数。

## 🚀 生产环境建议

1. **修改所有默认密码**
2. **使用外部 MySQL**（而不是容器内的 MySQL）
3. **配置 Nacos 集群模式**
4. **配置 RocketMQ 主从模式**
5. **使用外部 Redis 集群**
6. **配置数据备份策略**
7. **监控服务健康状态**

## 📚 相关文档

- [Nacos 官方文档](https://nacos.io/docs/latest/)
- [RocketMQ 官方文档](https://rocketmq.apache.org/docs/)
- [Redis 官方文档](https://redis.io/docs/)
- [Docker Compose 文档](https://docs.docker.com/compose/)

## 💡 提示

- 首次启动可能需要几分钟时间，请耐心等待
- 建议使用 `docker-compose logs -f` 查看启动日志
- 所有服务都配置了健康检查，可以使用 `docker-compose ps` 查看健康状态

