# 旧项目剩余价值评估

## 📊 总体评估

**结论**：旧项目仍有**部分参考价值**，但**核心代码已迁移完成**。建议保留作为**参考实现**，但**不建议继续维护**。

---

## ✅ 仍有价值的组件（可作为参考）

### 1. 配置模式参考（中等价值）

| 组件 | 价值 | 说明 |
|------|------|------|
| `VideoFrameExtractionConfig` | ⭐⭐⭐ | 视频抽帧配置结构，可作为新项目配置设计的参考 |
| `VideoFrameExtractionConfigService` | ⭐⭐ | 配置加载模式（文件+配置中心），可参考实现方式 |
| `LLMConfigHelper` | ⭐⭐ | LLM配置解析模式，可参考 |
| `ExpireConfigService` | ⭐ | 过期数据检查逻辑，可参考（但需适配新架构） |

**建议**：
- 参考配置结构设计，但不直接迁移
- 新项目应该使用更通用的配置抽象

### 2. LLM集成参考（中等价值）

| 组件 | 价值 | 说明 |
|------|------|------|
| `llm/langchain4j/*` | ⭐⭐⭐ | LangChain4j集成代码，可作为实现参考 |
| `LLMClient.java` | ⭐⭐ | LLM客户端封装模式，可参考 |
| `LLMCacheService.java` | ⭐⭐⭐ | LLM缓存服务，**建议迁移**到新项目 |

**建议**：
- `LLMCacheService` 应该迁移到新项目（`state-store`模块）
- LangChain4j集成代码可作为参考实现

### 3. 工具类（低-中等价值）

| 组件 | 价值 | 说明 |
|------|------|------|
| `GjsonUtils.java` | ⭐⭐ | JSON路径提取工具，已迁移 |
| `HttpUtils.java` | ⭐⭐ | HTTP工具类，已迁移 |
| `QuestUtils.java` | ⭐ | 问卷工具类，特定业务，不建议迁移 |

**建议**：
- 通用工具类已迁移
- 业务特定工具类不建议迁移

### 4. 离线处理参考（低价值）

| 组件 | 价值 | 说明 |
|------|------|------|
| `offline/image/ImageClassifier.java` | ⭐ | 图片分类逻辑，可作为校验规则插件参考 |
| `offline/image/DedupStrategyFactory.java` | ⭐ | 去重策略工厂，可作为校验规则插件参考 |

**建议**：
- 可作为新项目 `governance-core/validator/rule/` 的参考实现
- 但不直接迁移，需要重构为插件化

### 5. Pipeline辅助阶段（低价值）

| 组件 | 价值 | 说明 |
|------|------|------|
| `pipeline/stages/ClassifyStage.java` | ⭐ | 分类阶段，可参考实现校验规则 |
| `pipeline/stages/DedupStage.java` | ⭐ | 去重阶段，可参考实现校验规则 |
| `pipeline/stages/VideoMetadataStage.java` | ⭐⭐ | 视频元数据解析，可参考实现元数据提取逻辑 |

**建议**：
- 可作为新项目校验规则和元数据提取的参考
- 但不直接迁移，需要重构

---

## ❌ 无价值的组件（可删除）

### 1. 业务模块（100% 业务噪音）

| 组件 | 文件数 | 建议 |
|------|--------|------|
| `business/bsaas/*` | ~30 | ❌ 删除 |
| `business/couponsp/*` | ~10 | ❌ 删除 |
| `business/gdbubble/*` | ~20 | ❌ 删除 |
| `business/gdspecialprice/*` | ~13 | ❌ 删除 |
| `business/xlbubble/*` | ~11 | ❌ 删除 |
| `business/xlprice/*` | ~12 | ❌ 删除 |
| **总计** | **~96** | **❌ 全部删除** |

**原因**：
- 特定业务逻辑，不适合通用编排系统
- 新项目通过插件化方式支持业务逻辑

### 2. 业务抽象（不适合新架构）

| 组件 | 建议 |
|------|------|
| `llm/Business.java` | ❌ 删除（新项目使用StepExecutor） |
| `llm/BusinessFactory.java` | ❌ 删除 |
| `llm/BusinessRegistry.java` | ❌ 删除 |
| `llm/Poster.java` | ❌ 删除（新项目使用校验规则） |
| `llm/Sinker.java` | ❌ 删除（新项目使用结果存储抽象） |
| `llm/MessageHandler.java` | ❌ 删除（新项目使用AgentTaskOrchestrator） |

**原因**：
- 新项目采用不同的架构模式（StepExecutor vs Business）
- 这些抽象不适合通用编排系统

### 3. 特定业务配置和服务

| 组件 | 建议 |
|------|------|
| `BusinessConfigService.java` | ❌ 删除 |
| `PriceFittingConfigService.java` | ❌ 删除 |
| `PatrolConfigService.java` | ❌ 删除 |
| `service/BackstraceService.java` | ❌ 删除 |
| `scheduler/tasks/PriceFittingTask.java` | ❌ 删除 |
| `scheduler/tasks/IntegrityCheckTask.java` | ❌ 删除 |

**原因**：
- 特定业务逻辑，不适合通用系统
- 新项目通过配置和插件化支持

---

## 📋 迁移建议清单

### 高优先级（建议迁移）

1. **LLM缓存服务**
   - `LLMCacheService.java` → `state-store/src/main/java/.../LLMCacheService.java`
   - 价值：通用功能，新项目需要

### 中优先级（可参考实现）

1. **LangChain4j集成代码**
   - `llm/langchain4j/*` → 参考实现 `InferenceExecutor.LLMServiceClient`
   - 价值：已有实现，可参考

2. **视频元数据解析**
   - `pipeline/stages/VideoMetadataStage.java` → 参考实现元数据提取
   - 价值：元数据提取逻辑可参考

### 低优先级（仅参考）

1. **配置模式**
   - `VideoFrameExtractionConfig` → 参考配置结构设计
   - 价值：配置设计模式可参考

2. **校验规则参考**
   - `offline/image/ImageClassifier.java` → 参考实现校验规则插件
   - `pipeline/stages/ClassifyStage.java` → 参考实现校验规则
   - 价值：可作为插件实现的参考

---

## 🎯 最终建议

### 1. 保留策略

**建议保留旧项目作为参考实现**，但：
- ✅ 标记为 `deprecated` 或 `archive`
- ✅ 在 README 中说明已迁移到新项目
- ✅ 不再接受新的功能开发
- ✅ 仅用于参考和问题排查

### 2. 清理策略

**可以删除的内容**：
- ❌ 所有 `business/` 目录（~96个文件）
- ❌ 业务抽象类（Business, Poster, Sinker等）
- ❌ 特定业务配置和服务
- ❌ 已迁移的工具类（如果新项目已完整迁移）

**建议保留的内容**：
- ✅ 配置结构参考（`VideoFrameExtractionConfig`等）
- ✅ LLM集成代码（`llm/langchain4j/*`）
- ✅ 离线处理参考（`offline/image/*`）
- ✅ Pipeline辅助阶段（作为参考）

### 3. 迁移优先级

**立即迁移**：
1. `LLMCacheService` → 新项目 `state-store` 模块

**参考实现**：
1. LangChain4j集成 → 实现 `InferenceExecutor.LLMServiceClient`
2. 视频元数据解析 → 实现元数据提取逻辑

**仅参考**：
1. 配置模式 → 设计新项目的配置结构
2. 校验规则 → 实现 `governance-core/validator/rule/` 插件

---

## 📊 价值评估总结

| 类别 | 文件数 | 价值 | 建议 |
|------|--------|------|------|
| **核心基础设施** | ~50 | ✅ 已迁移 | 已完成 |
| **业务模块** | ~96 | ❌ 无价值 | 删除 |
| **业务抽象** | ~10 | ❌ 无价值 | 删除 |
| **配置参考** | ~10 | ⭐⭐ 中等 | 保留参考 |
| **LLM集成** | ~8 | ⭐⭐⭐ 高 | 迁移/参考 |
| **工具类** | ~5 | ⭐⭐ 中等 | 已迁移 |
| **离线处理** | ~20 | ⭐ 低 | 参考 |
| **总计** | ~199 | - | - |

**结论**：
- ✅ **核心价值已迁移**：约 50 个核心文件已迁移到新项目
- ⭐ **参考价值**：约 43 个文件可作为参考实现
- ❌ **无价值**：约 106 个业务文件可删除

**旧项目剩余价值：约 20-25%**（主要是参考价值，而非直接使用价值）

---

## 🚀 下一步行动

1. **迁移 LLMCacheService**（高优先级）
2. **参考 LangChain4j 集成**实现 `LLMServiceClient`
3. **标记旧项目为 archive**，不再维护
4. **清理业务代码**（可选，如果确定不再需要）

---

**总结**：旧项目仍有**部分参考价值**，但**核心代码已迁移完成**。建议保留作为参考，但不再继续维护。

