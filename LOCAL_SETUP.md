# 本地启动所需组件清单

本文档列出了在本地环境启动 `LLM Data Collect Service` 所需的所有组件及其配置方法。

## 📋 必需组件（Core Components）

### 1. **Java 开发环境**
- **JDK 17+** 
- **Maven 3.6+**
- 验证方式：
```bash
java -version    # 应显示 17 或更高版本
mvn -version     # 应显示 3.6 或更高版本
```

### 2. **MySQL 8.0+**
- **用途**：数据持久化存储
- **安装方式**：
  - macOS: `brew install mysql@8.0`
  - Linux: `sudo apt-get install mysql-server` 或 `sudo yum install mysql-server`
  - Windows: 从 [MySQL 官网](https://dev.mysql.com/downloads/mysql/) 下载安装

- **配置要求**：
```sql
-- 创建数据库
CREATE DATABASE mars_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（可选）
CREATE USER 'mars_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON mars_data.* TO 'mars_user'@'localhost';
FLUSH PRIVILEGES;
```

- **环境变量配置**：
```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/mars_data?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="your_password"
```

### 3. **Redis 6.0+**
- **用途**：缓存、分布式锁
- **安装方式**：
  - macOS: `brew install redis`
  - Linux: `sudo apt-get install redis-server` 或 `sudo yum install redis`
  - Windows: 从 [Redis 官网](https://redis.io/download) 下载或使用 WSL

- **启动方式**：
```bash
redis-server    # 默认端口 6379
```

- **环境变量配置**：
```bash
export REDIS_ADDRESS="redis://localhost:6379"
export REDIS_PASSWORD=""  # 如果有密码则设置
```

### 4. **FFmpeg 和 FFprobe**
- **用途**：视频处理、帧提取（如果使用视频处理功能）
- **安装方式**：
  - macOS: `brew install ffmpeg`
  - Linux: `sudo apt-get install ffmpeg` 或 `sudo yum install ffmpeg`
  - Windows: 从 [FFmpeg 官网](https://ffmpeg.org/download.html) 下载并配置 PATH

- **验证方式**：
```bash
ffmpeg -version
ffprobe -version
```

- **环境变量配置**（可选，如果不配置则使用系统 PATH）：
```bash
export FFMPEG_PATH="/usr/local/bin/ffmpeg"
export FFPROBE_PATH="/usr/local/bin/ffprobe"
```

### 5. **S3 存储（MinIO 或 AWS S3）**
- **用途**：视频文件存储（必需，如果使用视频处理功能）
- **MinIO（推荐本地开发）**：
  ```bash
  # 安装 MinIO
  brew install minio/stable/minio
  
  # 启动 MinIO（创建数据目录）
  mkdir -p ~/minio-data
  minio server ~/minio-data --console-address ":9001"
  
  # 默认访问地址：
  # API: http://localhost:9000
  # Console: http://localhost:9001
  # 默认用户名密码: minioadmin / minioadmin
  ```

- **配置要求**（需要在 Apollo 或代码中配置 S3 存储信息）：
  - Endpoint: `http://localhost:9000` (MinIO) 或 AWS S3 endpoint
  - Access Key: `minioadmin` (MinIO) 或 AWS Access Key
  - Secret Key: `minioadmin` (MinIO) 或 AWS Secret Key

---

## 🔧 可选组件（Optional Components）

### 6. **RocketMQ 5.0+**
- **用途**：消息队列（如果使用 MQ 功能）
- **安装方式**：
  ```bash
  # 下载 RocketMQ
  wget https://archive.apache.org/dist/rocketmq/5.1.4/rocketmq-all-5.1.4-bin-release.zip
  unzip rocketmq-all-5.1.4-bin-release.zip
  cd rocketmq-all-5.1.4-bin-release
  
  # 启动 NameServer
  sh bin/mqnamesrv
  
  # 启动 Broker（新终端）
  sh bin/mqbroker -n localhost:9876
  ```

- **环境变量配置**：
```bash
export ROCKETMQ_NAME_SERVER="localhost:9876"
export ROCKETMQ_PRODUCER_GROUP="llm-data-collect-producer-group"
```

### 7. **Apollo 配置中心**
- **用途**：集中配置管理（生产环境推荐，本地开发可选）
- **本地开发**：可以不使用 Apollo，直接使用 `application.yml` 配置文件
- **生产环境**：需要配置 Apollo 客户端连接信息
- **环境变量**：
```bash
export APP_ENV="dev"  # dev/test/prod
```

### 8. **StatsD（监控）**
- **用途**：指标收集和监控（可选）
- **本地开发**：可以不启动，监控相关代码会优雅降级
- **安装方式**：
```bash
# Docker 方式（推荐）
docker run -d -p 8125:8125/udp -p 8126:8126 --name statsd graphiteapp/graphite-statsd

# 或使用 Node.js 版本
npm install -g statsd
```

---

## 🚀 快速启动指南

### 步骤 1: 安装必需组件
```bash
# 1. 确保 JDK 17+ 和 Maven 已安装
java -version
mvn -version

# 2. 安装并启动 MySQL
brew install mysql@8.0  # macOS
brew services start mysql@8.0

# 3. 安装并启动 Redis
brew install redis  # macOS
brew services start redis

# 4. 安装 FFmpeg（如果需要视频处理）
brew install ffmpeg

# 5. 安装并启动 MinIO（如果需要视频处理）
brew install minio/stable/minio
mkdir -p ~/minio-data
minio server ~/minio-data --console-address ":9001"
```

### 步骤 2: 创建数据库
```bash
mysql -u root -p
```
```sql
CREATE DATABASE mars_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 步骤 3: 配置环境变量
创建 `.env` 文件或直接导出环境变量：
```bash
# 数据库配置
export MYSQL_URL="jdbc:mysql://localhost:3306/mars_data?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="your_password"

# Redis 配置
export REDIS_ADDRESS="redis://localhost:6379"
export REDIS_PASSWORD=""

# 环境标识
export APP_ENV="dev"

# FFmpeg 路径（可选，如果不配置则使用系统 PATH）
export FFMPEG_PATH="ffmpeg"
export FFPROBE_PATH="ffprobe"

# RocketMQ（如果使用）
export ROCKETMQ_NAME_SERVER="localhost:9876"

# StatsD（如果使用）
export STATSD_HOST="localhost"
export STATSD_PORT="8125"
```

### 步骤 4: 编译和运行
```bash
# 编译项目
mvn clean package

# 运行应用
java -jar target/LLM-data-collect-1.0.0-SNAPSHOT.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

### 步骤 5: 验证启动
- 检查健康状态: `curl http://localhost:8080/actuator/health`
- 查看日志: `tail -f log/llm-data-collect.log`

---

## 📝 最小化启动配置

如果只需要测试基本功能，**最小化配置**需要：
1. ✅ JDK 17+
2. ✅ Maven 3.6+
3. ✅ MySQL 8.0+
4. ✅ Redis 6.0+

**可选组件**（可根据业务需求选择）：
- FFmpeg/FFprobe（视频处理功能）
- MinIO/S3（视频存储功能）
- RocketMQ（消息队列功能）
- Apollo（配置中心，本地可用 application.yml 替代）
- StatsD（监控功能）

---

## 🐛 常见问题

### Q1: MySQL 连接失败
- 检查 MySQL 服务是否启动: `brew services list` 或 `sudo systemctl status mysql`
- 验证连接信息是否正确: `mysql -u root -p -h localhost`
- 确认数据库已创建

### Q2: Redis 连接失败
- 检查 Redis 服务是否启动: `redis-cli ping`（应返回 `PONG`）
- 检查端口是否被占用: `lsof -i :6379`

### Q3: FFmpeg 未找到
- 确认 FFmpeg 已安装: `which ffmpeg`
- 设置环境变量: `export FFMPEG_PATH="/usr/local/bin/ffmpeg"`

### Q4: S3/MinIO 连接失败
- 检查 MinIO 是否启动: 访问 `http://localhost:9001`
- 验证 Access Key 和 Secret Key
- 检查防火墙/网络配置

### Q5: 端口冲突
- 应用默认端口: `8080`（HTTP），`6060`（pprof）
- 检查端口占用: `lsof -i :8080`
- 如需修改，编辑 `application.yml` 或设置环境变量

---

## 📚 相关文档

- [README.md](./README.md) - 项目概述
- [application.yml](./src/main/resources/application.yml) - 配置文件模板
- [BVideoPipeline.java](./src/main/java/com/wuxiansheng/shieldarch/marsdata/scripts/BVideoPipeline.java) - 视频处理管道示例

---

## 💡 提示

1. **本地开发推荐使用 MinIO** 而非 AWS S3，可以完全本地化运行
2. **Apollo 配置中心**在本地开发时可不用，直接使用 `application.yml`
3. **RocketMQ** 如果不需要消息队列功能，可以不启动
4. **StatsD** 监控工具不影响核心功能，可以后续添加
5. 所有配置都可以通过**环境变量**覆盖 `application.yml` 中的默认值

