# Project Nebula

基于 Spring Boot 的 LLM 数据收集与处理服务。

## 项目简介

通过 RocketMQ 消费消息，使用 LLM 进行智能推理处理，并将结果存储到 Hive 和 MySQL。

## 技术栈

- **框架**: Spring Boot 3.2.0
- **语言**: Java 21
- **消息队列**: RocketMQ 5.1.4
- **数据库**: MySQL 8.0+ (MyBatis Plus + Druid)
- **缓存**: Redis (Redisson)
- **配置中心**: Nacos Config（已替换 Apollo）
- **服务发现**: Nacos（已替换 DiSF）
- **对象存储**: MinIO S3
- **视频处理**: JavaCV Platform (FFmpeg/OpenCV)
- **LLM 框架**: LangChain4j 0.29.1
- **监控**: Prometheus + Grafana + AlertManager（已替换 StatsD/Odin）

## 快速开始

### 环境要求

- JDK 21+
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
java -jar target/Project-Nebula-1.0.0-SNAPSHOT.jar
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

## 核心特性

- ✅ **多业务支持**: 6 个业务模块（BSaaS、券包、高德、小拉等）
- ✅ **多模态 LLM**: 支持文本 + 图片的 LLM 调用
- ✅ **流式视频处理**: 内存流处理，避免本地文件存储
- ✅ **完整监控**: Prometheus + Grafana + AlertManager
- ✅ **配置中心**: Nacos 配置管理，支持本地回退
- ✅ **服务发现**: Nacos 统一服务发现
- ✅ **定时任务**: 价格拟合、数据完整性检查、视频列表扫描

## 已完成的替换

- ✅ **DiSF → Nacos**: 服务发现已完全替换
- ✅ **Apollo → Nacos**: 配置中心已完全替换
- ✅ **StatsD/Odin → Prometheus**: 监控已完全替换

## 待完成的工作

无

## 详细文档

- [项目实现总结](docs/PROJECT_IMPLEMENTATION_SUMMARY.md) - 完整的项目架构和实现说明
- [滴滴组件替换清单](DIDI_COMPONENTS_REPLACEMENT.md) - 组件替换状态
- [Prometheus 监控指南](docker/README_PROMETHEUS.md) - 监控配置说明
- [Nacos 服务发现](src/main/java/com/wuxiansheng/shieldarch/marsdata/utils/README_NACOS_SERVICE_DISCOVERY.md) - 服务发现使用说明
