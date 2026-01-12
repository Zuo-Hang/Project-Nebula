# LangChain4j 集成说明

## 📋 概述

本项目已集成 LangChain4j，用于替换核心的 LLM 接口调用部分。采用渐进式重构策略，保持向后兼容。

## 🏗️ 架构设计

```
LLMClient (原有接口，保持不变)
    ↓
requestLLM() 方法
    ↓
┌─────────────────┬─────────────────┐
│  LangChain4j    │   Legacy 实现    │
│  (新实现)       │   (回退方案)     │
└─────────────────┴─────────────────┘
    ↓                    ↓
LangChain4jLLMService  HTTP 直接调用
    ↓
DiSFChatModel (适配 DiSF)
    ↓
LLM API
```

## 📦 核心组件

### 1. `DiSFChatModel`
自定义 ChatModel，适配 DiSF 服务发现和现有的 LLM 服务。

**功能**：
- 实现 `ChatLanguageModel` 接口
- 适配 DiSF 服务发现
- 支持多模态（文本 + 图片）
- 保持与现有 API 格式兼容

### 2. `LangChain4jLLMService`
LangChain4j LLM 服务封装。

**功能**：
- 管理 ChatModel 实例（按业务缓存）
- 复用现有的配置管理
- 支持多模态调用

### 3. `LLMClient`（重构）
核心 LLM 客户端，支持两种实现方式。

**功能**：
- 保持原有接口不变（向后兼容）
- 支持 LangChain4j 和 Legacy 两种实现
- 自动回退机制

## ⚙️ 配置

### application.yml

```yaml
llm:
  use-langchain4j: true  # 是否使用 LangChain4j（默认：true）
```

### 环境变量

```bash
export LLM_USE_LANGCHAIN4J=true  # 或 false 使用原有实现
```

## 🔄 使用方式

### 现有代码无需修改

```java
// 现有代码保持不变
LLMClient.RequestLLMRequest request = llmClient.newRequestLLMRequest(
    businessName, picUrl, prompt);
LLMClient.LLMResponse response = llmClient.requestLLM(request);
String content = response.getChoices().get(0).getMessage().getContent();
```

### 内部实现自动切换

- **启用 LangChain4j**（默认）：使用 `LangChain4jLLMService` → `DiSFChatModel`
- **禁用 LangChain4j**：使用原有的 HTTP 直接调用

## 🔧 核心特性

### 1. 向后兼容
- ✅ 保持 `LLMClient` 接口不变
- ✅ 保持 `LLMResponse` 数据结构不变
- ✅ 现有业务代码无需修改

### 2. 自动回退
- ✅ LangChain4j 调用失败时自动回退到原有实现
- ✅ 确保服务稳定性

### 3. 配置管理
- ✅ 复用现有的配置管理
- ✅ 复用现有的 DiSF 服务发现
- ✅ 支持按业务配置不同的 LLM 参数

### 4. 多模态支持
- ✅ 支持文本 + 图片的多模态调用
- ✅ 通过 ThreadLocal 传递图片 URL

## 📊 重构前后对比

### 重构前

```java
// 直接 HTTP 调用
HttpRequest httpRequest = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .header("Content-Type", "application/json")
    .POST(...)
    .build();
HttpResponse<String> response = httpClient.send(httpRequest, ...);
LLMResponse llmResponse = objectMapper.readValue(response.body(), LLMResponse.class);
```

### 重构后

```java
// 使用 LangChain4j 统一 API
ChatLanguageModel model = getOrCreateChatModel(businessName);
Response<AiMessage> response = model.generateWithImage(prompt, imageUrl);
String content = response.content().text();
```

## 🚀 优势

1. **统一 API**：使用 LangChain4j 的标准接口
2. **易于扩展**：未来可以轻松切换不同的 LLM 提供商
3. **代码简化**：减少 HTTP 调用相关的样板代码
4. **向后兼容**：现有代码无需修改
5. **自动回退**：确保服务稳定性

## ⚠️ 注意事项

1. **多模态消息处理**
   - 当前通过 ThreadLocal 传递图片 URL
   - 未来可以升级到 LangChain4j 的原生多模态支持

2. **配置切换**
   - 可以通过 `llm.use-langchain4j` 配置项切换实现
   - 建议在生产环境先灰度测试

3. **性能影响**
   - LangChain4j 抽象层可能带来轻微性能开销
   - 建议进行性能测试对比

4. **依赖管理**
   - 新增了 LangChain4j 依赖
   - 注意版本兼容性

## 🔍 调试和监控

### 日志

- LangChain4j 调用会记录 `[LangChain4j]` 前缀的日志
- Legacy 调用会记录 `[Legacy]` 前缀的日志

### 指标上报

- 保持现有的 StatsD 指标上报
- 指标名称：`llm_req`

## 📝 后续优化

1. **升级多模态支持**
   - 使用 LangChain4j 原生的 `ImageContent` 支持
   - 移除 ThreadLocal 传递方式

2. **增强错误处理**
   - 更详细的错误信息
   - 更好的重试机制

3. **性能优化**
   - ChatModel 实例池化
   - 连接复用

4. **功能扩展**
   - 支持流式响应
   - 支持 Function Calling
   - 支持 RAG（检索增强生成）

## 🔗 相关文档

- [LangChain4j 官方文档](https://docs.langchain4j.info/)
- [LANGCHAIN4J_REFACTOR_ANALYSIS.md](../LANGCHAIN4J_REFACTOR_ANALYSIS.md) - 重构分析报告

