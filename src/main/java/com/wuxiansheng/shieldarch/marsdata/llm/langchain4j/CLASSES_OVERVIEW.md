# LangChain4j 目录类说明

## 📁 目录结构

```
src/main/java/com/wuxiansheng/shieldarch/marsdata/llm/langchain4j/
├── DiSFChatModel.java              # 当前使用的 ChatModel（ThreadLocal 方式）
├── DiSFChatModelNative.java        # 原生 ImageContent 版本的 ChatModel（示例/未来版本）
├── LangChain4jLLMService.java     # 当前使用的 LLM 服务（ThreadLocal 方式）
├── LangChain4jLLMServiceNative.java # 原生 ImageContent 版本的 LLM 服务（示例/未来版本）
├── README_LANGCHAIN4J.md          # LangChain4j 集成说明文档
├── IMAGE_CONTENT_COMPARISON.md    # ThreadLocal vs 原生 ImageContent 对比文档
└── IMAGE_CONTENT_API_NOTE.md      # ImageContent API 使用说明
```

## 🔧 核心类说明

### 1. `DiSFChatModel.java` ⭐ **当前使用**

**用途**：自定义 ChatModel，适配 DiSF 服务发现和现有的 LLM 服务

**功能**：
- 实现 `ChatLanguageModel` 接口
- 适配 DiSF 服务发现获取 LLM 端点
- 支持多模态（文本 + 图片），通过 **ThreadLocal** 传递图片 URL
- 将 LangChain4j 的消息格式转换为 OpenAI 兼容的 API 格式

**使用场景**：
- 当前生产环境使用
- 被 `LangChain4jLLMService` 调用

**特点**：
- ✅ 已实现并投入使用
- ⚠️ 使用 ThreadLocal 传递图片，需要手动管理生命周期

---

### 2. `LangChain4jLLMService.java` ⭐ **当前使用**

**用途**：LangChain4j LLM 服务封装，提供统一的 LLM 调用接口

**功能**：
- 管理 ChatModel 实例（按业务名称缓存）
- 复用现有的配置管理
- 支持多模态调用（文本 + 图片）
- 被 `LLMClient` 调用

**使用场景**：
- 当前生产环境使用
- 作为 `LLMClient.requestLLM()` 的底层实现

**特点**：
- ✅ 已实现并投入使用
- ✅ 与现有配置系统集成
- ⚠️ 使用 ThreadLocal 方式传递图片

---

### 3. `DiSFChatModelNative.java` 🔮 **示例/未来版本**

**用途**：使用 LangChain4j 原生 `ImageContent` 的 ChatModel 实现

**功能**：
- 与 `DiSFChatModel` 功能相同
- 但使用原生的 `ImageContent` 和 `TextContent`
- **无需 ThreadLocal**，图片信息直接包含在消息对象中

**使用场景**：
- 作为未来升级的参考实现
- 展示如何使用原生 ImageContent
- 目前**未投入使用**

**特点**：
- ⚠️ 使用反射获取 ImageContent 信息（因为 API 可能因版本而异）
- ✅ 代码更清晰，无需管理 ThreadLocal
- 📝 需要根据实际的 LangChain4j 版本调整 API 调用

---

### 4. `LangChain4jLLMServiceNative.java` 🔮 **示例/未来版本**

**用途**：使用 LangChain4j 原生 `ImageContent` 的 LLM 服务实现

**功能**：
- 与 `LangChain4jLLMService` 功能相同
- 但使用原生的 `ImageContent.from(imageUrl)` 创建图片内容
- 展示如何构建多模态消息

**使用场景**：
- 作为未来升级的参考实现
- 展示原生方式的使用方法
- 目前**未投入使用**

**特点**：
- ✅ 代码示例清晰，展示原生方式
- ✅ 支持 URL 和 Base64 两种图片输入方式
- 📝 需要配合 `DiSFChatModelNative` 使用

---

## 📚 文档说明

### 5. `README_LANGCHAIN4J.md`

**用途**：LangChain4j 集成的总体说明文档

**内容**：
- 架构设计
- 配置说明
- 使用方式
- 核心特性
- 后续优化建议

---

### 6. `IMAGE_CONTENT_COMPARISON.md`

**用途**：对比 ThreadLocal 方式和原生 ImageContent 方式的区别

**内容**：
- 两种实现方式的详细对比
- 代码示例对比
- 性能对比
- 迁移建议

---

### 7. `IMAGE_CONTENT_API_NOTE.md`

**用途**：说明如何确定 LangChain4j ImageContent 的正确 API

**内容**：
- API 版本差异说明
- 如何查看正确的 API
- 推荐的修复步骤

---

## 🔄 类之间的关系

```
LLMClient (原有接口)
    ↓
LangChain4jLLMService (当前使用) ⭐
    ↓
DiSFChatModel (当前使用) ⭐
    ↓
LLM API (通过 DiSF 服务发现)

---

未来升级路径：

LLMClient (原有接口)
    ↓
LangChain4jLLMServiceNative (未来版本) 🔮
    ↓
DiSFChatModelNative (未来版本) 🔮
    ↓
LLM API (通过 DiSF 服务发现)
```

## 📊 使用状态总结

| 类名 | 状态 | 用途 | 备注 |
|------|------|------|------|
| `DiSFChatModel` | ✅ 使用中 | 当前 ChatModel 实现 | ThreadLocal 方式 |
| `LangChain4jLLMService` | ✅ 使用中 | 当前 LLM 服务 | 被 LLMClient 调用 |
| `DiSFChatModelNative` | 🔮 示例 | 原生方式示例 | 未投入使用 |
| `LangChain4jLLMServiceNative` | 🔮 示例 | 原生方式示例 | 未投入使用 |

## 🎯 建议

1. **当前使用**：
   - `DiSFChatModel` + `LangChain4jLLMService` 是生产环境使用的实现

2. **未来升级**：
   - 可以逐步迁移到 `DiSFChatModelNative` + `LangChain4jLLMServiceNative`
   - 需要先确定 LangChain4j 的 ImageContent API
   - 参考 `IMAGE_CONTENT_COMPARISON.md` 了解迁移步骤

3. **文档参考**：
   - 集成说明：`README_LANGCHAIN4J.md`
   - 方式对比：`IMAGE_CONTENT_COMPARISON.md`
   - API 说明：`IMAGE_CONTENT_API_NOTE.md`

