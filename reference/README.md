# 参考代码目录

## 📋 目录说明

本目录存放从旧项目中提取的**仅作为参考**的代码实现，用于理解业务逻辑和设计模式。

**重要提示**：
- ⚠️ 这些代码**不应直接迁移**到新项目
- ⚠️ 这些代码**仅作为参考实现**
- ✅ 新项目应该通过**插件化方式**实现类似功能

---

## 📁 目录结构

```
reference/
└── old-project/              # 旧项目参考代码
    ├── pipeline-stages/      # Pipeline辅助阶段
    ├── offline-processing/   # 离线处理参考
    ├── config-examples/      # 配置结构参考
    └── README.md            # 详细说明
```

---

## 🎯 参考内容

### 1. Pipeline辅助阶段
- **ClassifyStage** - 图片分类逻辑（可作为校验规则插件参考）
- **DedupStage** - 图片去重逻辑（可作为校验规则插件参考）
- **CleanupStage** - 清理逻辑（可在执行器中实现）

### 2. 离线处理参考
- **ImageClassifier** - 图片分类器（可作为校验规则插件参考）
- **DedupStrategy** - 去重策略（可作为校验规则插件参考）
- **IDStrategy** - ID生成策略（可作为校验规则插件参考）

### 3. 配置结构参考
- **VideoFrameExtractionConfig** - 配置结构设计参考

---

## 📚 详细说明

请查看 [old-project/README.md](old-project/README.md) 获取详细的文件说明和使用指南。

---

**最后更新**：2024年（迁移完成后）

