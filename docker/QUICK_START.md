# 快速启动指南

## ✅ 检查清单

在启动项目之前，确保以下组件都已正确配置：

### 1. 启动 Docker 服务

```bash
cd docker
./start.sh
```

等待所有服务启动完成（约 1-2 分钟）。

### 2. 验证服务状态

```bash
# 检查所有服务是否运行
docker-compose ps

# 应该看到所有服务都是 "Up" 状态
```

### 3. 访问服务控制台

- **Nacos**: http://localhost:8848/nacos (用户名: `nacos`, 密码: `nacos`)
- **RocketMQ Console**: http://localhost:8081

## 🔧 配置检查

### Redis 配置

项目已配置为连接本地 Docker Redis：
- 地址: `localhost:6379`
- 密码: `redis123456` (默认值)

### RocketMQ 配置

项目已配置为连接本地 Docker RocketMQ：
- NameServer: `localhost:9876`

### Nacos 配置

项目已配置为连接本地 Docker Nacos：
- 地址: `127.0.0.1:8848`
- 用户名: `nacos` (默认值)
- 密码: `nacos` (默认值)

## 🚀 启动项目

### 方式 1: 使用 Maven

```bash
mvn spring-boot:run
```

### 方式 2: 使用 IDE

直接运行 `LLMDataCollectApplication.java`

## ✅ 验证连接

### 1. 检查 Redis 连接

项目启动后，查看日志中是否有 Redis 连接成功的消息。

或者手动测试：
```bash
docker exec -it mars-data-redis redis-cli -a redis123456 ping
# 应该返回: PONG
```

### 2. 检查 RocketMQ 连接

访问 RocketMQ 控制台：http://localhost:8081

查看项目日志，确认 Producer 和 Consumer 是否正常启动。

### 3. 检查 Nacos 连接

访问 Nacos 控制台：http://localhost:8848/nacos

查看项目日志，确认 Nacos 服务发现是否初始化成功。

## 🐛 常见问题

### 问题 1: Redis 连接失败

**症状**: 日志显示 `Unable to connect to Redis`

**解决**:
1. 检查 Redis 容器是否运行: `docker-compose ps redis`
2. 检查端口是否被占用: `netstat -an | grep 6379`
3. 验证密码是否正确: `docker exec -it mars-data-redis redis-cli -a redis123456 ping`

### 问题 2: RocketMQ 连接失败

**症状**: 日志显示 `connect to [localhost:9876] failed`

**解决**:
1. 检查 NameServer 是否运行: `docker-compose ps rocketmq-nameserver`
2. 检查 Broker 是否运行: `docker-compose ps rocketmq-broker`
3. 等待服务完全启动（可能需要 30-60 秒）

### 问题 3: Nacos 连接失败

**症状**: 日志显示 `Nacos 服务发现初始化失败`

**解决**:
1. 检查 Nacos 是否运行: `docker-compose ps nacos`
2. 检查 MySQL 是否运行: `docker-compose ps mysql`
3. 访问 Nacos 控制台确认服务正常
4. 查看 Nacos 日志: `docker-compose logs nacos`

### 问题 4: 端口冲突

**症状**: Docker 启动失败，提示端口已被占用

**解决**:
1. 检查端口占用: `netstat -an | grep [端口号]`
2. 停止占用端口的服务
3. 或修改 `docker-compose.yml` 中的端口映射

## 📝 环境变量覆盖

如果需要覆盖默认配置，可以设置环境变量：

```bash
# Redis 密码
export REDIS_PASSWORD=your_password

# Nacos 配置
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos

# RocketMQ
export ROCKETMQ_NAME_SERVER=localhost:9876
```

## 🎯 完整启动流程

```bash
# 1. 启动 Docker 服务
cd docker
./start.sh

# 2. 等待服务启动（约 1-2 分钟）
sleep 60

# 3. 验证服务状态
docker-compose ps

# 4. 启动 Java 项目
cd ..
mvn spring-boot:run

# 5. 查看日志确认连接成功
```

## ✅ 成功标志

当看到以下日志时，说明所有组件都已成功连接：

```
✅ Redis 连接成功
✅ RocketMQ Producer 启动成功
✅ RocketMQ Consumer 启动成功
✅ Nacos 服务发现初始化成功
✅ 应用启动完成
```

## 🔗 相关文档

- [Docker README](./README.md) - 详细的 Docker 配置说明
- [Nacos 服务发现文档](../src/main/java/com/wuxiansheng/shieldarch/marsdata/utils/README_NACOS_SERVICE_DISCOVERY.md)

