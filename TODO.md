# TODO 清单

> 项目待实现功能与改进建议

---

## 📋 目录

1. [核心功能待实现](#核心功能待实现)
2. [质量治理层](#质量治理层)
3. [测试与质量保证](#测试与质量保证)
4. [性能优化](#性能优化)
5. [功能扩展](#功能扩展)
6. [工程实践改进](#工程实践改进)
7. [文档完善](#文档完善)

---

## 🎯 核心功能待实现

### 1. 图片信息提取 Agent
**优先级**: ⭐⭐⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现"给图片和要求，解析出可用信息"的 Agent 功能

**任务清单**:
- [ ] 实现 `ImageInfoExtractorExecutor` StepExecutor
- [ ] 集成 OCR + VLM + LLM 工具链
- [ ] 实现结构化信息提取（JSON 格式）
- [ ] 实现动态工具选择器（根据需求选择工具）
- [ ] 实现动态 DAG 生成（根据工具依赖关系）
- [ ] 前端页面：图片信息提取交互界面
- [ ] 支持多种提取场景（商品信息、表单信息、文档信息等）

**相关文件**:
- `step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/executors/ImageInfoExtractorExecutor.java` (待创建)

---

## 🛡️ 质量治理层

### 2. DualCheckValidator 完善
**优先级**: ⭐⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善双路校验器，实现规则校验和语义校验

**任务清单**:
- [ ] 完善规则校验逻辑
- [ ] 实现业务规则插件注册机制
- [ ] 实现语义校验（使用轻量级 LLM 检查逻辑矛盾）
- [ ] 实现校验结果缓存
- [ ] 添加更多业务规则（价格波动检查、字段完整性检查等）

**相关文件**:
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/validator/DualCheckValidator.java`
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/validator/rule/` (待创建规则类)

### 3. SelfCorrectionHandler 完善
**优先级**: ⭐⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善自愈处理器，实现更智能的错误分析和 Prompt 改进

**任务清单**:
- [ ] 完善错误类型分析
- [ ] 实现更智能的 Prompt 改进策略
- [ ] 实现重试次数限制和退避策略
- [ ] 添加自愈效果评估（成功率统计）
- [ ] 实现自愈历史记录和回放

**相关文件**:
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/handler/SelfCorrectionHandler.java`

### 4. 业务规则插件
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现可插拔的业务规则系统

**任务清单**:
- [ ] 实现 `ImageClassificationRule`（图片分类规则）
- [ ] 实现 `ImageDeduplicationRule`（图片去重规则）
- [ ] 实现 `PriceValidationRule`（价格校验规则）
- [ ] 实现规则注册机制（`BusinessStrategyRegistry`）
- [ ] 实现规则优先级和冲突处理

**相关文件**:
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/validator/rule/ImageClassificationRule.java` (待创建)
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/validator/rule/ImageDeduplicationRule.java` (待创建)
- `governance-core/src/main/java/com/wuxiansheng/shieldarch/governance/validator/rule/PriceValidationRule.java` (待创建)

---

## 🧪 测试与质量保证

### 5. 单元测试
**优先级**: ⭐⭐⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 为核心模块添加单元测试，提高代码覆盖率

**任务清单**:
- [ ] `AgentTaskOrchestrator` 单元测试
- [ ] `TaskStateMachine` 单元测试
- [ ] `StepExecutor` 实现类单元测试
- [ ] `DualCheckValidator` 单元测试
- [ ] `SelfCorrectionHandler` 单元测试
- [ ] `PromptManager` 单元测试
- [ ] `LocalLLMService` 单元测试
- [ ] OCR 服务单元测试（Python）

**测试框架**:
- Java: JUnit 5 + Mockito + Testcontainers
- Python: pytest + pytest-mock

**目标覆盖率**: 80%+

### 6. 集成测试
**优先级**: ⭐⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现端到端的集成测试

**任务清单**:
- [ ] 任务提交流程集成测试
- [ ] OCR + LLM 推理流程集成测试
- [ ] 自愈重试流程集成测试
- [ ] 状态持久化和恢复测试
- [ ] 背压控制测试

### 7. 压力测试
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 进行系统压力测试，获取性能数据

**任务清单**:
- [ ] 设计压力测试场景
- [ ] 实现压力测试脚本（JMeter / Gatling）
- [ ] 测试并发任务处理能力
- [ ] 测试背压控制效果
- [ ] 测试系统稳定性（长时间运行）
- [ ] 生成性能测试报告

**目标指标**:
- 并发任务数: 100+
- P95 延迟: < 5s
- P99 延迟: < 10s
- 系统可用性: 99.9%

---

## ⚡ 性能优化

### 8. LLM 缓存优化
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 优化 LLM 结果缓存策略，减少重复调用

**任务清单**:
- [ ] 实现更智能的缓存键生成（考虑 Prompt 相似度）
- [ ] 实现缓存预热机制
- [ ] 实现缓存失效策略（TTL、LRU）
- [ ] 添加缓存命中率监控

**相关文件**:
- `state-store/src/main/java/com/wuxiansheng/shieldarch/statestore/LLMCacheService.java`

### 9. 向量数据库集成
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 集成向量数据库，支持相似度搜索和 Few-shot 自动选择

**任务清单**:
- [ ] 选择向量数据库（Milvus / Qdrant / Weaviate）
- [ ] 实现向量存储服务
- [ ] 集成到 `SimilarityExampleSelector`
- [ ] 实现 Embedding 生成（使用本地模型或 API）
- [ ] 实现相似度搜索优化

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/prompt/fewshot/SimilarityExampleSelector.java` (TODO: 集成向量数据库)

### 10. 异步执行优化
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 优化异步执行引擎，提高吞吐量

**任务清单**:
- [ ] 实现更细粒度的异步控制
- [ ] 优化 CompletableFuture 链式调用
- [ ] 实现任务优先级队列
- [ ] 实现任务超时和取消机制

---

## 🚀 功能扩展

### 11. 动态 DAG 生成
**优先级**: ⭐⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 根据任务需求动态生成执行计划（DAG）

**任务清单**:
- [ ] 实现工具依赖关系定义
- [ ] 实现 DAG 生成算法
- [ ] 实现 DAG 验证（检测循环依赖）
- [ ] 实现 DAG 可视化（前端展示）

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/DAGGenerator.java` (待创建)

### 12. PDF 支持
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 支持 PDF 文件处理（转换为图片后识别）

**任务清单**:
- [ ] 实现 PDF 转图片功能（PDFBox / Apache PDFBox）
- [ ] 支持多页 PDF 处理
- [ ] 集成到文件上传接口
- [ ] 前端支持 PDF 文件上传

**相关文件**:
- `step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/executors/PDFProcessorExecutor.java` (待创建)

### 13. 定时任务系统
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现定时任务调度系统

**任务清单**:
- [ ] 迁移或实现 Scheduler
- [ ] 实现 `IntegrityCheckTask`（完整性检查任务）
- [ ] 实现 `PriceFittingTask`（价格拟合任务）
- [ ] 实现 `VideoListTask`（视频列表任务）
- [ ] 实现任务注册和配置机制

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/config/SchedulerConfig.java` (TODO: 迁移 Scheduler)

### 14. 更多 StepExecutor 实现
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现更多业务场景的执行器

**任务清单**:
- [ ] `VideoClassificationExecutor`（视频分类）
- [ ] `ObjectDetectionExecutor`（目标检测）
- [ ] `ImageEnhancementExecutor`（图片增强）
- [ ] `DataExportExecutor`（数据导出）

---

## 🔧 工程实践改进

### 15. CI/CD 流程
**优先级**: ⭐⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 建立完整的 CI/CD 流程

**任务清单**:
- [ ] 配置 GitHub Actions / GitLab CI
- [ ] 实现自动化测试（单元测试 + 集成测试）
- [ ] 实现自动化构建和打包
- [ ] 实现自动化部署（Docker 镜像）
- [ ] 实现代码质量检查（SonarQube）
- [ ] 实现自动化回滚机制

### 16. Docker 容器化
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 完善 Docker 容器化部署

**任务清单**:
- [ ] 优化 Dockerfile（多阶段构建）
- [ ] 实现 Docker Compose 完整配置
- [ ] 实现健康检查机制
- [ ] 实现日志收集（ELK / Loki）
- [ ] 实现配置管理（ConfigMap / Secret）

### 17. 监控告警完善
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善监控告警体系

**任务清单**:
- [ ] 实现关键指标告警规则
- [ ] 实现告警通知（邮件 / 钉钉 / 企业微信）
- [ ] 实现告警聚合和去重
- [ ] 实现告警升级机制
- [ ] 实现自定义 Dashboard（Grafana）

### 18. 日志系统优化
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 优化日志系统，支持结构化日志和日志聚合

**任务清单**:
- [ ] 实现结构化日志（JSON 格式）
- [ ] 实现日志级别动态调整
- [ ] 集成日志聚合系统（ELK / Loki）
- [ ] 实现日志查询和分析
- [ ] 实现敏感信息脱敏

---

## 📚 文档完善

### 19. API 文档
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善 API 文档

**任务清单**:
- [ ] 使用 Swagger / OpenAPI 生成 API 文档
- [ ] 添加 API 使用示例
- [ ] 添加错误码说明
- [ ] 添加限流和认证说明

### 20. 架构设计文档
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善架构设计文档

**任务清单**:
- [ ] 完善系统架构图（C4 模型）
- [ ] 添加数据流图
- [ ] 添加部署架构图
- [ ] 添加技术选型说明

### 21. 开发指南
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善开发指南

**任务清单**:
- [ ] 添加新功能开发流程
- [ ] 添加代码规范说明
- [ ] 添加 Git 工作流说明
- [ ] 添加问题排查指南

---

## 🔍 代码质量改进

### 22. LangChain4j 自定义 ChatModel 实现
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现自定义 ChatLanguageModel，支持服务发现和 HTTP 调用

**任务清单**:
- [ ] 实现自定义 `ChatLanguageModel`
- [ ] 集成服务发现（Nacos）
- [ ] 实现 HTTP 客户端（OpenAI 兼容格式）
- [ ] 处理多模态消息（TextContent + ImageContent）
- [ ] 实现响应解析和错误处理

**相关文件**:
- `step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/executors/LangChain4jLLMServiceClient.java` (TODO: 实现自定义 ChatLanguageModel)

### 23. 任务取消功能
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现任务取消功能

**任务清单**:
- [ ] 实现任务取消接口
- [ ] 实现正在执行任务的取消逻辑
- [ ] 实现资源清理（释放信号量、关闭连接等）
- [ ] 实现取消状态持久化

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/service/TaskService.java` (TODO: 实现任务取消逻辑)

### 24. 文件上传到存储
**优先级**: ⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现处理后的文件上传到对象存储

**任务清单**:
- [ ] 实现文件上传到 S3 / MinIO
- [ ] 实现上传进度跟踪
- [ ] 实现上传失败重试
- [ ] 实现文件清理策略

**相关文件**:
- `step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/executors/FrameExtractExecutor.java` (TODO: 实现上传逻辑)

### 25. 监控类迁移
**优先级**: ⭐⭐  
**状态**: 📋 待实现  
**描述**: 迁移旧项目的监控类

**任务清单**:
- [ ] 迁移监控工具类
- [ ] 集成到新项目的监控体系
- [ ] 更新相关引用

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/io/PoiService.java` (TODO: 迁移监控类)
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/io/QuestService.java` (TODO: 迁移监控类)
- `state-store/src/main/java/com/wuxiansheng/shieldarch/statestore/RedisWrapper.java` (TODO: 迁移监控类)

---

## 🎨 前端功能扩展

### 26. OCR 自动调用集成
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 在本地 LLM 页面自动调用 OCR 服务

**任务清单**:
- [ ] 实现图片上传后自动 OCR 识别
- [ ] 将 OCR 结果传递给 LLM
- [ ] 优化用户体验（显示 OCR 识别进度）

**相关文件**:
- `frontend/src/pages/LocalLLM.tsx` (TODO: 上传图片后，系统将自动调用OCR服务生成识别结果)

### 27. 任务执行可视化
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现任务执行过程的可视化展示

**任务清单**:
- [ ] 实现 DAG 可视化（展示执行步骤）
- [ ] 实现实时状态更新（WebSocket）
- [ ] 实现执行日志实时展示
- [ ] 实现性能指标图表

### 28. 更多前端页面
**优先级**: ⭐⭐  
**状态**: 📋 待实现  
**描述**: 添加更多功能页面

**任务清单**:
- [ ] 图片信息提取页面
- [ ] 任务监控 Dashboard
- [ ] 系统配置页面
- [ ] 用户管理页面

---

## 📊 数据与统计

### 29. 成本监控完善
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善成本监控功能

**任务清单**:
- [ ] 实现按时间窗口分组统计
- [ ] 实现成本趋势分析
- [ ] 实现成本预测
- [ ] 实现成本告警

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/prompt/evaluation/CostMonitor.java` (TODO: 实现按时间窗口分组统计)

### 30. Prompt 优化器完善
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善自动 Prompt 优化功能

**任务清单**:
- [ ] 实现高阶模型调用（用于优化）
- [ ] 实现 MIPRO 优化器完整逻辑
- [ ] 实现优化效果评估
- [ ] 实现自动回滚机制

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/prompt/AutoPromptOptimizer.java` (TODO: 实现高阶模型调用)
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/prompt/optimization/MIPROOptimizer.java` (TODO: 实际应该调用评估器进行评估)

### 31. 评估器数据查询
**优先级**: ⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 实现评估器从数据库查询数据

**任务清单**:
- [ ] 实现历史数据查询
- [ ] 实现失败案例查询
- [ ] 实现数据聚合和分析

**相关文件**:
- `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/orchestrator/prompt/PromptEvaluator.java` (TODO: 实际实现应该从数据库查询)

---

## 🔐 安全与合规

### 32. 认证授权
**优先级**: ⭐⭐⭐  
**状态**: 📋 待实现  
**描述**: 实现用户认证和授权

**任务清单**:
- [ ] 实现 JWT 认证
- [ ] 实现角色权限控制（RBAC）
- [ ] 实现 API 密钥管理
- [ ] 实现操作审计日志

### 33. 数据安全
**优先级**: ⭐⭐⭐  
**状态**: 🚧 部分实现  
**描述**: 完善数据安全机制

**任务清单**:
- [ ] 完善敏感信息脱敏
- [ ] 实现数据加密存储
- [ ] 实现数据访问控制
- [ ] 实现数据备份和恢复

---

## 📝 其他改进

### 34. 许可证确定
**优先级**: ⭐  
**状态**: 📋 待确定  
**描述**: 确定项目许可证

**任务清单**:
- [ ] 选择合适的开源许可证（Apache 2.0 / MIT / GPL）
- [ ] 添加 LICENSE 文件
- [ ] 更新 README 中的许可证信息

**相关文件**:
- `README.md` (许可证: [待定])

### 35. 国际化支持
**优先级**: ⭐⭐  
**状态**: 📋 待实现  
**描述**: 支持多语言

**任务清单**:
- [ ] 实现前端国际化（i18n）
- [ ] 支持中文、英文
- [ ] 实现语言切换功能

---

## 📈 优先级说明

- ⭐⭐⭐⭐⭐: 最高优先级，核心功能，影响系统可用性
- ⭐⭐⭐⭐: 高优先级，重要功能，影响用户体验
- ⭐⭐⭐: 中优先级，增强功能，提升系统能力
- ⭐⭐: 低优先级，优化功能，锦上添花
- ⭐: 最低优先级，长期规划

## 📊 状态说明

- ✅ 已完成
- 🚧 进行中
- 📋 待实现
- ⏸️ 已暂停
- ❌ 已取消

---

**最后更新**: 2026-01-15  
**维护者**: Project-Nebula Team

