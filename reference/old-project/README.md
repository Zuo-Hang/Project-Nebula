# 旧项目参考代码

## 📋 目录说明

本目录存放从旧项目（`LLM-data-collect`）中提取的**仅作为参考**的代码实现。

**重要提示**：
- ⚠️ 这些代码**不应直接迁移**到新项目
- ⚠️ 这些代码**仅作为参考实现**，用于理解业务逻辑和设计模式
- ✅ 新项目应该通过**插件化方式**（StepExecutor、校验规则插件）实现类似功能

---

## 📁 目录结构

```
reference/old-project/
├── pipeline-stages/          # Pipeline辅助阶段（可作为校验规则插件参考）
│   ├── ClassifyStage.java    # 图片分类阶段
│   ├── DedupStage.java       # 图片去重阶段
│   ├── CleanupStage.java    # 清理阶段
│   ├── ClassificationResult.java
│   └── ClassificationSummary.java
│
├── offline-processing/        # 离线处理参考（可作为校验规则插件参考）
│   ├── ImageClassifier.java  # 图片分类器
│   ├── DedupStrategyFactory.java  # 去重策略工厂
│   ├── DedupStrategy.java    # 去重策略接口
│   ├── ClassificationMatch.java
│   ├── PageDedupInput.java
│   ├── PageDedupResult.java
│   ├── PageDedupSummary.java
│   ├── GlobalIDDedup.java
│   ├── SlidingWindowIDDedup.java
│   ├── CoverageMinSetDedup.java
│   ├── IDStrategy.java       # ID生成策略接口
│   ├── IDStrategyFactory.java  # ID策略工厂
│   ├── RegStrategy.java      # 正则策略
│   └── OrderListStrategy.java  # 订单列表策略
│
└── config-examples/          # 配置结构参考
    └── VideoFrameExtractionConfig.java  # 视频抽帧配置结构
```

**文件统计**：
- Pipeline辅助阶段：5 个文件
- 离线处理参考：14 个文件
- 配置结构参考：1 个文件
- **总计**：**20 个参考文件**

---

## 🎯 参考用途

### 1. Pipeline辅助阶段 → 校验规则插件

**参考文件**：
- `pipeline-stages/ClassifyStage.java` - 图片分类逻辑
- `pipeline-stages/DedupStage.java` - 图片去重逻辑
- `offline-processing/ImageClassifier.java` - 分类器实现
- `offline-processing/DedupStrategyFactory.java` - 去重策略工厂

**新项目实现方向**：
- 实现 `governance-core/validator/rule/` 下的校验规则插件
- 参考分类和去重逻辑，实现插件化的校验规则
- 使用 `DualCheckValidator` 框架

**示例**：
```java
// 新项目中的校验规则插件（参考 ClassifyStage）
@Component
public class ImageClassificationRule implements ValidationRule {
    // 参考 ClassifyStage 的分类逻辑
    // 但使用新的 TaskContext 和 StepResult
}
```

### 2. 清理逻辑参考

**参考文件**：
- `pipeline-stages/CleanupStage.java` - 本地文件清理逻辑

**新项目实现方向**：
- 在 `FrameExtractExecutor` 或 `AgentTaskOrchestrator` 中实现清理逻辑
- 参考 `CleanupStage` 的文件删除和目录清理逻辑

### 3. 配置结构参考

**参考文件**：
- `config-examples/VideoFrameExtractionConfig.java` - 视频抽帧配置结构

**新项目实现方向**：
- 参考配置结构设计新项目的配置类
- 理解页面分类、去重、元数据解析的配置模式
- 适配到新项目的配置管理（Nacos Config）

---

## 📝 文件说明

### Pipeline辅助阶段

#### ClassifyStage.java
- **功能**：根据OCR文本对图片进行分类
- **参考价值**：分类逻辑、关键词匹配、正则验证
- **新项目对应**：`governance-core/validator/rule/ImageClassificationRule`

#### DedupStage.java
- **功能**：根据页面类型和唯一ID对图片进行去重
- **参考价值**：去重策略选择、ID生成、去重统计
- **新项目对应**：`governance-core/validator/rule/ImageDeduplicationRule`

#### CleanupStage.java
- **功能**：清理本地临时文件
- **参考价值**：文件删除、目录清理逻辑
- **新项目对应**：在 `FrameExtractExecutor` 或任务完成后清理

### 离线处理参考

#### ImageClassifier.java
- **功能**：图片分类器核心实现
- **参考价值**：关键词匹配、排除词、正则验证、最小匹配数
- **新项目对应**：校验规则插件的分类逻辑

#### DedupStrategyFactory.java
- **功能**：去重策略工厂
- **参考价值**：策略模式、参数解析
- **新项目对应**：校验规则插件的策略选择

#### DedupStrategy 相关类
- **功能**：各种去重策略实现
  - `GlobalIDDedup` - 全局ID去重
  - `SlidingWindowIDDedup` - 滑动窗口去重
  - `CoverageMinSetDedup` - 最小覆盖集去重
- **参考价值**：去重算法实现
- **新项目对应**：校验规则插件的去重逻辑

### 配置结构参考

#### VideoFrameExtractionConfig.java
- **功能**：视频抽帧链路配置结构
- **参考价值**：
  - 页面分类配置（keywords, exclude, min_matches, verify_regexes）
  - 页面去重配置（strategy_class, rule, id, param）
  - 视频元数据解析配置（filename, normalize, validation）
- **新项目对应**：新项目的配置结构设计参考

---

## ⚠️ 注意事项

1. **不要直接复制代码**
   - 这些代码使用旧项目的架构（PipelineStage, Business等）
   - 新项目使用不同的架构（StepExecutor, 校验规则插件）

2. **理解设计模式**
   - 关注逻辑和算法，而不是具体实现
   - 参考策略模式、工厂模式等设计模式

3. **适配新架构**
   - 将逻辑适配到新项目的架构
   - 使用 `TaskContext` 而不是 `PipelineContext`
   - 使用 `StepResult` 而不是直接修改上下文

4. **插件化实现**
   - 新项目通过插件化方式支持业务逻辑
   - 实现 `ValidationRule` 接口而不是直接修改核心代码

---

## 🔄 迁移对照表

| 旧项目 | 新项目对应 | 状态 |
|--------|----------|------|
| `ClassifyStage` | `governance-core/validator/rule/ImageClassificationRule` | ⚠️ 待实现 |
| `DedupStage` | `governance-core/validator/rule/ImageDeduplicationRule` | ⚠️ 待实现 |
| `CleanupStage` | `FrameExtractExecutor` 或任务清理逻辑 | ⚠️ 待实现 |
| `ImageClassifier` | 校验规则插件的分类逻辑 | ⚠️ 待实现 |
| `DedupStrategyFactory` | 校验规则插件的策略选择 | ⚠️ 待实现 |
| `VideoFrameExtractionConfig` | 新项目的配置结构设计 | ⚠️ 待设计 |

---

## 📚 相关文档

- [架构对比分析](../ARCHITECTURE_COMPARISON.md) - 新旧架构对比
- [旧项目价值评估](../OLD_PROJECT_VALUE_ASSESSMENT.md) - 详细的价值评估
- [旧项目最终状态](../OLD_PROJECT_FINAL_STATUS.md) - 最终状态总结

---

**最后更新**：2024年（迁移完成后）

