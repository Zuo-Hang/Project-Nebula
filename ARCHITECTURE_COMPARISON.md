# 新旧项目架构对比分析

## 📊 核心结论

**新项目（AI Agent Orchestrator）** 是一个**通用的智能体编排系统**，专注于：
- 状态机驱动的任务编排
- 弹性调度与背压控制
- 质量治理与自愈能力
- 全链路可观测性

**旧项目（LLM-data-collect）** 是一个**业务数据收集系统**，包含大量特定业务逻辑。

---

## ✅ 需要的核心基础设施（应迁移）

### 1. 视频处理核心（Pipeline 模块）

| 旧项目路径 | 新项目对应 | 说明 | 优先级 |
|-----------|----------|------|--------|
| `pipeline/interfaces/VideoPort.java` | `step-executors/executors/FrameExtractExecutor.java` | 视频抽帧接口 → StepExecutor实现 | 🔴 高 |
| `pipeline/interfaces/OCRPort.java` | `step-executors/executors/InferenceExecutor.java` | OCR识别接口 → StepExecutor实现 | 🔴 高 |
| `pipeline/interfaces/StoragePort.java` | `step-executors/io/S3Client.java` | 存储接口（已迁移S3Client） | ✅ 已迁移 |
| `pipeline/stages/VideoProcessStage.java` | `step-executors/executors/FrameExtractExecutor.java` | 视频处理阶段 → 抽帧执行器 | 🔴 高 |
| `pipeline/stages/OCRStage.java` | `step-executors/executors/InferenceExecutor.java` | OCR阶段 → 推理执行器 | 🔴 高 |
| `pipeline/stages/ClassifyStage.java` | `governance-core/validator/` | 分类阶段 → 校验器 | 🟡 中 |
| `pipeline/stages/DedupStage.java` | `governance-core/validator/rule/` | 去重阶段 → 校验规则 | 🟡 中 |
| `pipeline/stages/ListStage.java` | `orchestrator-core/bootstrap/S3ScannerTrigger.java` | S3扫描阶段 → 定时扫描触发器 | 🔴 高 |
| `pipeline/stages/MQStage.java` | `orchestrator-core/bootstrap/MQConsumer.java` | MQ阶段 → 消息消费 | 🔴 高 |
| `pipeline/runner/PipelineRunner.java` | `orchestrator-core/orchestrator/AgentTaskOrchestrator.java` | 管道运行器 → 编排器核心 | 🔴 高 |
| `pipeline/context/PipelineContext.java` | `orchestrator-core/orchestrator/TaskStateMachine.java` | 管道上下文 → 状态机 | 🔴 高 |

**核心价值**：
- 视频流式抽帧（内存处理，不落盘）
- OCR批量识别
- 异步处理流程

### 2. 调度器核心（Scheduler 模块）

| 旧项目路径 | 新项目对应 | 说明 | 优先级 |
|-----------|----------|------|--------|
| `scheduler/Scheduler.java` | `orchestrator-core/orchestrator/AgentTaskOrchestrator.java` | 调度器 → 编排器（需重构） | 🔴 高 |
| `scheduler/Task.java` | `orchestrator-core/orchestrator/step/StepExecutor.java` | 任务接口 → 步骤执行器接口 | 🔴 高 |
| `scheduler/LockedTask.java` | `orchestrator-core/orchestrator/step/StepExecutor.java` | 分布式锁任务 → 步骤执行器（含锁） | 🟡 中 |
| `scheduler/tasks/VideoListTask.java` | `orchestrator-core/bootstrap/S3ScannerTrigger.java` | S3扫描任务 → 定时扫描触发器 | 🔴 高 |

**核心价值**：
- 分布式锁机制
- Cron表达式调度
- 任务执行指标上报

### 3. LLM 服务核心（LLM 模块 - 部分）

| 旧项目路径 | 新项目对应 | 说明 | 优先级 |
|-----------|----------|------|--------|
| `llm/ReasonService.java` | `step-executors/executors/InferenceExecutor.java` | 推理服务 → 推理执行器 | 🔴 高 |
| `llm/ReasonRequest.java` | `orchestrator-core/orchestrator/step/StepRequest.java` | 推理请求 → 步骤请求 | 🔴 高 |
| `llm/ReasonResponse.java` | `orchestrator-core/orchestrator/step/StepResult.java` | 推理响应 → 步骤结果 | 🔴 高 |
| `llm/BusinessContext.java` | `orchestrator-core/orchestrator/TaskContext.java` | 业务上下文 → 任务上下文 | 🔴 高 |
| `llm/langchain4j/*` | `step-executors/executors/InferenceExecutor.java` | LangChain4j集成 → 推理执行器 | 🔴 高 |
| `llm/LLMClient.java` | `state-store/client/LLMServiceClient.java` | LLM客户端 → 外部服务客户端 | 🟡 中 |

**核心价值**：
- LLM调用抽象
- 批量推理
- LangChain4j集成

### 4. 消息队列核心（MQ 模块）

| 旧项目路径 | 新项目对应 | 说明 | 优先级 |
|-----------|----------|------|--------|
| `mq/Producer.java` | `orchestrator-core/bootstrap/MQProducer.java` | 消息生产者 | 🔴 高 |
| `mq/Consumer.java` | `orchestrator-core/bootstrap/MQConsumer.java` | 消息消费者 | 🔴 高 |

**核心价值**：
- 任务事件队列
- 解耦的缓冲层

### 5. 离线处理核心（Offline 模块 - 部分）

| 旧项目路径 | 新项目对应 | 说明 | 优先级 |
|-----------|----------|------|--------|
| `offline/video/VideoExtractor.java` | `step-executors/executors/FrameExtractExecutor.java` | 视频提取器 → 抽帧执行器 | 🔴 高 |
| `offline/image/ImageClassifier.java` | `governance-core/validator/rule/` | 图片分类器 → 校验规则 | 🟡 中 |

**核心价值**：
- 视频帧提取逻辑
- 图片分类逻辑

---

## ❌ 无用的业务噪音（不应迁移）

### 1. 业务模块（Business 模块 - 全部）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `business/bsaas/*` | BSaaS业务（司机、乘客、订单等） | 特定业务逻辑，新项目是通用编排系统 |
| `business/couponsp/*` | 券包人群标签识别 | 特定业务逻辑 |
| `business/gdbubble/*` | 高德冒泡业务 | 特定业务逻辑 |
| `business/gdspecialprice/*` | 高德特价业务 | 特定业务逻辑 |
| `business/xlbubble/*` | 小拉冒泡业务 | 特定业务逻辑 |
| `business/xlprice/*` | 小拉价格业务 | 特定业务逻辑 |

**总计**：约 100+ 个业务类文件

**原因**：
- 新项目是**通用编排系统**，不包含特定业务逻辑
- 业务逻辑应该通过**StepExecutor**和**校验规则**插件化实现
- 这些业务类耦合了特定的数据结构和处理流程

### 2. LLM 业务抽象（LLM 模块 - 部分）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `llm/Business.java` | 业务接口 | 新项目使用 StepExecutor，不需要 Business 抽象 |
| `llm/BusinessFactory.java` | 业务工厂 | 新项目使用 StepExecutor 注册机制 |
| `llm/BusinessRegistry.java` | 业务注册表 | 新项目使用 StepExecutor 注册机制 |
| `llm/Poster.java` | 后处理接口 | 新项目使用校验规则（DualCheckValidator） |
| `llm/MessageHandler.java` | 消息处理器 | 新项目使用 AgentTaskOrchestrator |
| `llm/Sinker.java` | 数据下沉接口 | 新项目使用结果存储抽象 |

**原因**：
- 新项目采用**StepExecutor**模式，更通用、更灵活
- 旧项目的 Business/Poster/Sinker 模式是业务导向的，不适合通用编排系统

### 3. 特定业务配置（Config 模块 - 部分）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `config/BusinessConfigService.java` | 业务配置服务 | 特定业务配置，新项目不需要 |
| `config/PriceFittingConfigService.java` | 价格拟合配置 | 特定业务配置 |
| `config/VideoFrameExtractionConfigService.java` | 视频抽帧配置 | 可保留，但需重构为通用配置 |
| `config/ExpireConfigService.java` | 过期配置 | 特定业务逻辑 |
| `config/PatrolConfigService.java` | 巡检配置 | 特定业务逻辑 |

**原因**：
- 新项目应该使用**通用的配置管理**（NacosConfigService）
- 特定业务配置应该通过**配置中心**或**环境变量**管理

### 4. 特定业务定时任务（Scheduler 模块 - 部分）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `scheduler/tasks/PriceFittingTask.java` | 价格拟合任务 | 特定业务逻辑 |
| `scheduler/tasks/IntegrityCheckTask.java` | 完整性检查任务 | 特定业务逻辑（高德冒泡） |
| `scheduler/repository/PriceFittingRepository.java` | 价格拟合仓库 | 特定业务数据访问 |
| `scheduler/repository/IntegrityRepository.java` | 完整性检查仓库 | 特定业务数据访问 |

**原因**：
- 新项目的定时任务应该是**通用的触发器**（如S3ScannerTrigger）
- 特定业务任务应该通过**StepExecutor**实现，而不是独立的定时任务

### 5. 离线处理业务逻辑（Offline 模块 - 部分）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `offline/text/*` | 文本处理 | 特定业务逻辑 |
| `offline/image/SlidingWindowIDDedup.java` | 滑动窗口去重 | 特定业务逻辑 |

**原因**：
- 新项目使用**通用的去重和校验机制**（DualCheckValidator）
- 特定业务逻辑应该通过**校验规则插件**实现

### 6. 特定业务服务（Service 模块）

| 旧项目路径 | 说明 | 为什么是噪音 |
|-----------|------|------------|
| `service/BackstraceService.java` | 回溯服务 | 特定业务逻辑 |

---

## 🎯 迁移优先级建议

### 🔴 高优先级（核心基础设施）

1. **Pipeline 核心接口和实现**
   - `VideoPort`, `OCRPort`, `StoragePort` 接口
   - `VideoProcessStage`, `OCRStage` 核心逻辑
   - `PipelineRunner` → `AgentTaskOrchestrator`
   - `PipelineContext` → `TaskStateMachine`

2. **Scheduler 核心**
   - `Scheduler` → `AgentTaskOrchestrator`（需重构）
   - `Task` → `StepExecutor` 接口
   - `VideoListTask` → `S3ScannerTrigger`

3. **LLM 服务核心**
   - `ReasonService` → `InferenceExecutor`
   - `ReasonRequest/Response` → `StepRequest/Result`
   - `BusinessContext` → `TaskContext`
   - LangChain4j 集成代码

4. **消息队列**
   - `Producer`, `Consumer` → `MQProducer`, `MQConsumer`

### 🟡 中优先级（可选择性迁移）

1. **Pipeline 辅助阶段**
   - `ClassifyStage` → 校验规则
   - `DedupStage` → 校验规则

2. **离线处理核心**
   - `VideoExtractor` 核心逻辑
   - `ImageClassifier` 核心逻辑

### ⚪ 低优先级（参考实现）

1. **特定业务逻辑**（仅作为参考，不迁移）
   - 业务模块代码（了解业务需求）
   - 特定业务配置（了解配置结构）

---

## 📋 迁移策略

### 策略1：核心抽象优先

1. **先迁移接口和抽象**
   - `StepExecutor` 接口（基于 `Task` 和 `PipelineStage`）
   - `TaskStateMachine`（基于 `PipelineContext`）
   - `AgentTaskOrchestrator`（基于 `PipelineRunner` 和 `Scheduler`）

2. **再迁移核心实现**
   - `FrameExtractExecutor`（基于 `VideoProcessStage`）
   - `InferenceExecutor`（基于 `OCRStage` 和 `ReasonService`）

3. **最后迁移辅助功能**
   - 校验规则（基于 `ClassifyStage`, `DedupStage`）
   - 自愈逻辑（新实现）

### 策略2：渐进式重构

1. **保留旧项目作为参考**
   - 不删除旧项目代码
   - 新项目逐步实现核心功能

2. **业务逻辑插件化**
   - 将特定业务逻辑抽象为**校验规则插件**
   - 通过配置中心动态加载规则

---

## 🔍 关键差异总结

| 维度 | 旧项目 | 新项目 |
|------|--------|--------|
| **架构模式** | 业务导向（Business/Poster/Sinker） | 编排导向（Orchestrator/StepExecutor） |
| **任务抽象** | Task（定时任务） | StepExecutor（步骤执行器） |
| **状态管理** | 无状态（PipelineContext临时） | 有状态（TaskStateMachine持久化） |
| **业务逻辑** | 硬编码在业务类中 | 插件化（校验规则） |
| **调度方式** | Cron定时任务 | 事件驱动（MQ + 状态机） |
| **扩展性** | 需要修改业务类 | 实现StepExecutor接口即可 |

---

## ✅ 最终建议

### 应该迁移的（核心基础设施）

1. ✅ **Pipeline 核心**：VideoPort, OCRPort, PipelineRunner, PipelineContext
2. ✅ **Scheduler 核心**：Scheduler, Task, LockedTask
3. ✅ **LLM 核心**：ReasonService, LangChain4j集成
4. ✅ **MQ 核心**：Producer, Consumer
5. ✅ **离线处理核心**：VideoExtractor核心逻辑

### 不应该迁移的（业务噪音）

1. ❌ **所有 business/ 目录**：100+ 业务类文件
2. ❌ **Business/Poster/Sinker 抽象**：不适合通用编排系统
3. ❌ **特定业务配置**：BusinessConfigService, PriceFittingConfigService等
4. ❌ **特定业务任务**：PriceFittingTask, IntegrityCheckTask
5. ❌ **特定业务服务**：BackstraceService

### 迁移后的重构方向

1. **Pipeline → StepExecutor**
   - `VideoProcessStage` → `FrameExtractExecutor`
   - `OCRStage` → `InferenceExecutor`

2. **Scheduler → AgentTaskOrchestrator**
   - `Scheduler` → `AgentTaskOrchestrator`（事件驱动）
   - `Task` → `StepExecutor`（步骤抽象）

3. **Business → 插件化**
   - 业务逻辑 → 校验规则插件（DualCheckValidator）
   - 后处理逻辑 → 自愈处理器（SelfCorrectionHandler）

---

---

## 📊 统计数据

| 类别 | 文件数量 | 占比 | 说明 |
|------|---------|------|------|
| **业务模块（business/）** | 92 | 42% | ❌ 业务噪音 |
| **核心基础设施** | 78 | 35% | ✅ 需要迁移（部分） |
| **其他（config, io, monitor等）** | 51 | 23% | ✅ 已迁移或待迁移 |
| **总计** | 221 | 100% | - |

**核心基础设施细分**：
- Pipeline 模块：~25 个文件（需要迁移核心部分）
- Scheduler 模块：~10 个文件（需要迁移核心部分）
- LLM 模块：~20 个文件（需要迁移核心部分）
- MQ 模块：~2 个文件（需要迁移）
- Offline 模块：~21 个文件（需要迁移核心部分）

**总结**：
- ✅ **需要迁移**：约 **40-50 个核心文件**（18-23%）
- ❌ **业务噪音**：约 **92 个业务文件**（42%）
- ⚠️ **待评估**：约 **79 个其他文件**（36%）

---

**最终结论**：旧项目约 **20%** 是核心基础设施（需要迁移），**42%** 是业务噪音（不应迁移），**38%** 是已迁移或待评估的配置/工具类。

