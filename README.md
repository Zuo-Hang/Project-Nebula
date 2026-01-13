# AI Agent Orchestrator

> 面向生产、治理AI不确定性的智能体编排系统

## 📋 项目简介

**AI Agent Orchestrator** 是一个具备状态、弹性、自愈和可观测性的分布式智能体编排系统。它不再是线性脚本，而是一个完整的分布式系统，用于编排和管理AI任务的生命周期，包括视频处理、OCR识别、LLM推理等复杂流程。

### 核心价值

- ✅ **状态管理**：基于状态机实现任务的精准一次（Exactly-once）和断点续传
- ✅ **弹性调度**：通过背压控制（Semaphore）防止下游服务过载
- ✅ **质量治理**：双路校验 + 自愈重试，主动治理AI幻觉
- ✅ **可观测性**：全链路指标监控，用数据证明系统价值
- ✅ **异步执行**：基于 CompletableFuture 的异步执行引擎
- ✅ **模块化设计**：清晰的层次划分，支持独立部署和扩展

## 🏗️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    数据输入与触发层                               │
├─────────────────────────────────────────────────────────────────┤
│  S3对象存储 → 定时扫描触发器 → 消息队列（Kafka/RabbitMQ）        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  智能体编排层（AgentTaskOrchestrator）            │
├─────────────────────────────────────────────────────────────────┤
│  任务状态机 → 异步执行引擎 → 步骤执行器                          │
│  （State Machine）  （CompletableFuture + Semaphore）           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌─────────────────────┴─────────────────────┐
        ↓                                           ↓
┌──────────────────────┐              ┌──────────────────────┐
│  流式感知算子        │              │  异步推理算子        │
│  StreamFrameExtractor│              │  AsyncInferenceWorker│
└──────────────────────┘              └──────────────────────┘
        │                                           │
        └─────────────────────┬─────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    质量与治理层                                   │
├─────────────────────────────────────────────────────────────────┤
│  双路校验算子 → 自愈重试流                                       │
│  （Dual-Check Validator）  （SelfCorrectionHandler）            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    数据持久层                                     │
├─────────────────────────────────────────────────────────────────┤
│  RedisStateStore（任务快照） → Hive/对象存储（结果）             │
│  → 向量数据库（embedding）                                       │
└─────────────────────────────────────────────────────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────────────┐
│              可观测性（贯穿全链路）                               │
│              Micrometer + Prometheus + Grafana                   │
└─────────────────────────────────────────────────────────────────┘
```

### 核心组件与职责

#### 1. 数据输入与触发层

- **S3增量扫描器**：定时调用S3 API，基于LastModified时间发现新视频，生成标准化任务事件
- **消息队列**：作为解耦的缓冲层，存放待处理任务。即使后端处理暂停，数据也不丢失

#### 2. 智能体编排层（大脑）

这是系统的唯一入口和总指挥，是一个独立的Spring Boot应用。

- **AgentTaskOrchestrator**：核心调度器
  - 输入：消费MQ中的任务事件
  - 核心逻辑：为每个任务初始化一个状态机实例，并持久化到Redis。然后根据预设或动态生成的DAG，异步调度各StepExecutor
  - 关键机制：通过Semaphore实现全局背压控制，限制发往下游推理服务的并发数
- **StepExecutor**：步骤执行器抽象。每个具体的步骤（如抽帧、检测）都是一个实现此接口的Bean

#### 3. 异步推理与执行层（四肢）

- **StreamFrameExtractor**：实现StepExecutor。内部用ProcessBuilder调用FFmpeg，实现视频流到内存帧的转换，通过回调将BufferedImage流转给下一步
- **AsyncInferenceWorker**：实现StepExecutor。它不直接调用模型，而是作为智能客户端：
  - 持有受信号量保护的HTTP客户端
  - 负责调用独立的模型微服务（如OCR服务、目标检测服务），并处理超时、重试
  - 将结果封装为StepResult，交给编排器

#### 4. 质量与治理层（免疫系统）

- **DualCheckValidator**：校验器
  - 规则校验：调用BusinessStrategyRegistry中注册的规则（如价格波动检查）
  - 语义校验：构造Prompt让轻量级LLM（如Qwen2-Chat）检查字段间逻辑矛盾
- **SelfCorrectionHandler**：自愈处理器。当校验失败时，根据错误类型和上下文，利用LLM重写或丰富最初的推理Prompt，然后要求AsyncInferenceWorker重新执行该步骤

#### 5. 数据持久层

- **RedisStateStore**：状态存储。以TaskId为Key，存储整个任务状态机的快照（JSON序列化）。这是实现Exactly-once和断点续传的核心
- **结果存储**：结构化结果写入Hive/MySQL，特征向量写入向量数据库，原始帧可选择性存入对象存储

#### 6. 可观测性（贯穿全身的神经系统）

- **Micrometer**：在每个关键步骤（StepExecutor执行前后、校验、自愈）打点，记录耗时、状态、Token用量
- **核心指标**：
  - `task_completion_time`：任务完成时间
  - `step_retry_count`：步骤重试次数
  - `llm_token_usage`：LLM Token使用量
  - `semaphore_queue_size`：背压排队长度
- **价值**：用数据证明"引入背压后MQ堆积率下降90%"

## 🎯 核心特性

### 1. 状态机驱动的任务编排

- 基于状态机实现任务的精准一次（Exactly-once）处理
- 支持断点续传，任务中断后可恢复执行
- 任务状态持久化到Redis，支持分布式环境

### 2. 弹性调度与背压控制

- 通过Semaphore实现全局背压控制
- 限制发往下游推理服务的并发数，防止服务过载
- 异步执行引擎基于CompletableFuture，提高吞吐量

### 3. 质量治理与自愈能力

- **双路校验**：规则校验 + 语义校验
- **自愈重试**：校验失败时自动生成反思Prompt，重新执行
- 主动治理AI幻觉，提高结果可靠性

### 4. 全链路可观测性

- 基于Micrometer的全链路指标监控
- 集成Prometheus + Grafana实现可视化
- 关键指标：任务完成时间、重试次数、Token用量、背压排队长度

### 5. 模块化设计

- 清晰的层次划分：编排层、执行层、治理层、持久层
- 支持独立部署和扩展
- 模型服务可独立部署为微服务

## 🛠️ 技术栈

### 核心框架
- **Spring Boot 3.2.0**：应用框架
- **Java 21**：开发语言

### 数据存储
- **MySQL 8.0+**：结构化数据存储（MyBatis Plus + Druid）
- **Redis (Redisson)**：状态存储和分布式锁
- **MinIO S3**：对象存储（视频、图片）
- **Hive**：大数据存储（可选）

### 消息队列
- **RocketMQ 5.1.4**：任务事件队列

### AI/ML 框架
- **LangChain4j 0.29.1**：LLM调用抽象
- **JavaCV Platform 1.5.9**：视频处理（FFmpeg/OpenCV封装）

### 服务治理
- **Nacos 2.3.0**：配置中心和服务发现

### 监控与可观测性
- **Prometheus**：指标收集
- **Grafana**：可视化仪表盘
- **Micrometer**：应用指标

### 工具库
- **Lombok**：代码简化
- **Jackson**：JSON处理
- **Apache Commons**：工具类

## 📁 项目结构

```
ai-agent-orchestrator/
├── orchestrator-core/          # 智能体编排层（主Spring Boot应用）
│   ├── orchestrator/           # 编排器核心
│   │   ├── AgentTaskOrchestrator.java    # 总调度器
│   │   ├── TaskStateMachine.java         # 状态机定义
│   │   ├── TaskContext.java              # 任务上下文
│   │   └── step/                          # 步骤执行器
│   │       ├── StepExecutor.java          # 步骤接口
│   │       ├── StepRequest.java            # 步骤请求
│   │       └── StepResult.java             # 步骤结果
│   ├── bootstrap/              # 启动类
│   │   ├── S3ScannerTrigger.java           # 定时扫描触发器
│   │   ├── MQConsumer.java                 # 任务消费入口
│   │   └── MQProducer.java                 # 消息生产者
│   ├── config/                 # 配置类（Nacos、MySQL、Redis、MQ等）
│   ├── controller/             # HTTP接口
│   ├── service/                # 服务层
│   ├── entity/                 # 实体类
│   ├── mapper/                 # MyBatis Mapper
│   ├── io/                     # IO操作（MySQL、POI、Quest等）
│   ├── monitor/                # 监控（Prometheus、Pprof）
│   └── utils/                  # 工具类
│
├── step-executors/             # 能力执行层（抽帧、推理等）
│   ├── executors/              # 执行器实现
│   │   ├── FrameExtractExecutor.java      # 视频抽帧执行器
│   │   ├── InferenceExecutor.java         # 推理执行器
│   │   ├── VideoExtractor.java            # 视频提取器
│   │   ├── VideoMetadataExtractor.java    # 视频元数据提取器
│   │   └── LangChain4jLLMServiceClient.java  # LangChain4j LLM客户端
│   └── io/                     # IO操作
│       ├── OcrClient.java                  # OCR客户端
│       └── S3Client.java                    # S3客户端
│
├── governance-core/            # 质量治理层（校验、自愈）
│   └── [待实现]
│
├── state-store/                # 持久层抽象与Redis实现
│   ├── LLMCacheService.java    # LLM缓存服务
│   ├── RedisWrapper.java       # Redis包装器
│   └── RedisLock.java          # 分布式锁
│
├── model-services/             # 独立的模型微服务
│   ├── ocr-service/            # OCR服务（可独立部署）
│   └── detection-service/      # 检测服务（可独立部署）
│
├── frontend/                   # 前端项目（React + TypeScript + Vite）
│   ├── src/                   # 源代码
│   ├── package.json           # 依赖配置
│   └── vite.config.ts         # Vite配置
├── demo-admin/                 # 演示前端（Streamlit，可选）
│   └── requirements.txt
│
├── mysql/                      # MySQL初始化脚本
│   └── init.sql
│
├── prometheus-grafana/         # 监控配置
│   ├── prometheus/
│   │   ├── prometheus.yml      # Prometheus配置
│   │   └── alert_rules.yml     # 告警规则
│   └── grafana/
│       └── provisioning/       # Grafana配置
│
├── reference/                  # 参考代码（旧项目参考实现）
│   └── old-project/            # 旧项目参考代码
│
├── docker-compose.yml          # Docker服务配置
├── pom.xml                     # 父POM
└── README.md                   # 本文档
```

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- Docker & Docker Compose（用于启动依赖服务）

### 启动依赖服务

```bash
# 启动所有依赖服务（Redis、RocketMQ、MySQL、Nacos、Prometheus、Grafana、AlertManager）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

### 编译项目

```bash
# 编译所有模块
mvn clean package

# 只编译特定模块
mvn clean package -pl orchestrator-core -am
```

### 运行应用

```bash
# 运行主应用
cd orchestrator-core
mvn spring-boot:run

# 或使用jar包运行
java -jar orchestrator-core/target/orchestrator-core-1.0.0-SNAPSHOT.jar
```

### 验证服务

```bash
# 健康检查
curl http://localhost:8080/api/health

# Actuator健康检查
curl http://localhost:8080/actuator/health

# Prometheus指标
curl http://localhost:8080/actuator/prometheus
```

### 访问监控面板

- **Grafana**: http://localhost:3000 (admin/admin123)
- **Prometheus**: http://localhost:9090
- **AlertManager**: http://localhost:9093
- **Nacos控制台**: http://localhost:8848/nacos (nacos/nacos)
- **RocketMQ控制台**: http://localhost:8080 (需要单独部署)

## 📊 核心指标

系统通过Micrometer暴露以下核心指标：

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `task_completion_time` | Histogram | 任务完成时间分布 |
| `step_retry_count` | Counter | 步骤重试次数 |
| `llm_token_usage` | Counter | LLM Token使用量 |
| `semaphore_queue_size` | Gauge | 背压排队长度 |
| `task_status_total` | Counter | 任务状态统计（按状态标签） |
| `step_execution_time` | Timer | 步骤执行耗时 |

## 🔄 任务处理流程

```
1. S3Scanner定时扫描，发现新视频
   ↓
2. 生成任务事件，发送到消息队列
   ↓
3. AgentTaskOrchestrator消费任务事件
   ↓
4. 初始化任务状态机，持久化到Redis
   ↓
5. 异步调度StepExecutor（受Semaphore背压控制）
   ↓
6. StreamFrameExtractor抽帧 → AsyncInferenceWorker推理
   ↓
7. DualCheckValidator校验结果
   ↓
8. 校验失败 → SelfCorrectionHandler自愈重试
   ↓
9. 校验通过 → 写入结果存储（Hive/MySQL/向量数据库）
   ↓
10. 更新任务状态，完成
```

## 🎓 架构价值

这个架构用标准的Java分布式系统架构，解决了AI时代特有的工程难题：

1. **状态管理**：通过状态机和Redis实现任务的精准一次和断点续传
2. **弹性调度**：通过背压控制防止下游服务过载，提高系统稳定性
3. **质量治理**：通过双路校验和自愈重试，主动治理AI幻觉
4. **可观测性**：全链路指标监控，用数据证明系统价值

## 📦 项目状态

### ✅ 已完成
- 核心基础设施迁移（100%）
- 编排器核心框架（AgentTaskOrchestrator、TaskStateMachine）
- 步骤执行器（FrameExtractExecutor、InferenceExecutor）
- LLM集成（LangChain4j、LLM缓存）
- 视频处理（VideoExtractor、VideoMetadataExtractor）
- 配置管理（Nacos Config）
- 服务发现（Nacos Service Registry）
- 监控系统（Prometheus + Grafana + AlertManager）
- 状态存储（Redis、LLM缓存）

### 🚧 进行中
- 质量治理层（DualCheckValidator、SelfCorrectionHandler）
- 更多StepExecutor实现

### 📋 待实现
- 完整的任务处理流程
- 更多业务规则插件
- 完整的测试覆盖

## 📝 开发指南

### 添加新的StepExecutor

1. 实现`StepExecutor`接口
2. 在`orchestrator-core`中注册为Spring Bean
3. 在`TaskStateMachine`中配置执行顺序

### 添加新的校验规则

1. 在`governance-core/validator/rule/`目录下创建规则类
2. 实现校验逻辑
3. 在`DualCheckValidator`中注册

### 添加新的模型服务

1. 在`model-services/`目录下创建新的服务模块
2. 实现服务接口
3. 在`AsyncInferenceWorker`中配置调用

## 📚 相关文档

- [TODO.md](TODO.md) - 组件迁移待办清单
- [架构对比分析](ARCHITECTURE_COMPARISON.md) - 新旧架构对比
- [迁移总结](MIGRATION_SUMMARY.md) - 组件迁移总结
- [旧项目最终状态](OLD_PROJECT_FINAL_STATUS.md) - 旧项目迁移完成状态
- [参考代码](reference/old-project/README.md) - 旧项目参考代码说明
- [归档说明](ARCHIVE.md) - 项目归档说明

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

## 📄 许可证

[待定]

---

**AI Agent Orchestrator** - 用分布式系统架构治理AI不确定性
