# LLM Data Collect Service

基于 Spring Boot 的 LLM 数据收集与处理服务。

## 项目简介

通过 RocketMQ 消费消息，使用 LLM 进行智能推理处理，并将结果存储到 Hive 和 MySQL。

## 技术栈

- Spring Boot 3.2.0
- Java 17
- RocketMQ 5.1.4
- MySQL 8.0+ (MyBatis Plus)
- Redis (Redisson)
- Apollo 配置中心
- MinIO S3

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- RocketMQ 5.0+ (可选)

### 配置

编辑 `src/main/resources/application.yml` 配置数据库、Redis、RocketMQ 等连接信息。

### 运行

```bash
# 编译打包
mvn clean package

# 运行
java -jar target/LLM-data-collect-1.0.0-SNAPSHOT.jar
```

## 业务模块

- **BSaaS**: 司机详情、乘客详情、订单列表等
- **券包人群标签识别**: 券包相关数据识别
- **高德业务**: 冒泡、特价业务
- **小拉业务**: 冒泡、价格业务

## API 接口

- `GET /api/health` - 健康检查
- `POST /api/backstrace` - 回溯接口
- `/actuator/health` - Actuator 健康检查

## 项目结构

```
src/main/java/com/wuxiansheng/shieldarch/marsdata/
├── business/      # 业务模块
├── config/        # 配置类
├── http/          # HTTP 接口
├── io/            # IO 操作
├── llm/           # LLM 服务
├── mq/            # 消息队列
├── scheduler/     # 定时任务
└── utils/         # 工具类
```

## 注意事项

- 部分内部 SDK（DiSF、DirPC、Dufe）需要替换为 Java 实现
- 生产环境建议通过环境变量或 Apollo 管理敏感配置
