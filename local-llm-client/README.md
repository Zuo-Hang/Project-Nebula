# Local LLM Client

> 本地大模型调用客户端 - 独立模块，最小依赖

## 📋 简介

这是一个独立的模块，用于调用本地部署的大模型（如 Ollama、vLLM 等）。

### 特点

- ✅ **独立模块**：不依赖主项目的复杂组件
- ✅ **最小依赖**：只依赖 Spring Boot Web 和 Lombok
- ✅ **可独立启动**：可以单独运行，不依赖其他服务
- ✅ **简单实现**：当前为框架结构，调用逻辑待实现

## 🚀 快速开始

### 1. 启动应用

```bash
cd local-llm-client
mvn spring-boot:run
```

### 2. 验证服务

```bash
# 健康检查
curl http://localhost:8081/api/llm/health
```

### 3. 测试推理接口（待实现）

```bash
curl -X POST http://localhost:8081/api/llm/infer \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "你好",
    "imageUrl": null,
    "ocrText": null
  }'
```

## 📁 项目结构

```
local-llm-client/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/wuxiansheng/shieldarch/llm/
│       │       ├── LocalLLMClientApplication.java  # 启动类
│       │       ├── controller/
│       │       │   └── LocalLLMController.java   # HTTP接口
│       │       └── service/
│       │           └── LocalLLMService.java       # 服务层（待实现）
│       └── resources/
│           └── application.yml                   # 配置文件
├── pom.xml
└── README.md
```

## 🔧 配置说明

### application.yml

```yaml
server:
  port: 8081  # 端口号

local-llm:
  ollama:
    base-url: http://localhost:11434
    model: qwen2.5:7b-instruct
    timeout: 60000
```

## 📝 待实现功能

### 1. LocalLLMService.infer()

需要实现：
- 连接本地模型服务（Ollama/vLLM）
- 构建请求（模型名称、prompt、参数）
- 发送 HTTP 请求
- 解析响应
- 错误处理

### 2. LocalLLMService.isServiceAvailable()

需要实现：
- 检查本地模型服务是否运行
- 检查模型是否已加载
- 返回服务状态

## 🔌 接口说明

### GET /api/llm/health

健康检查接口

**响应示例：**
```json
{
  "status": "UP",
  "service": "Local LLM Client",
  "serviceAvailable": false
}
```

### POST /api/llm/infer

调用本地大模型进行推理

**请求体：**
```json
{
  "prompt": "你好，请介绍一下你自己",
  "imageUrl": "http://localhost:9000/image.jpg",
  "ocrText": "OCR识别文本"
}
```

**响应示例：**
```json
{
  "success": true,
  "content": "本地模型调用功能待实现"
}
```

## 🎯 后续计划

1. 实现 Ollama 调用逻辑
2. 实现 vLLM 调用逻辑
3. 添加连接池管理
4. 添加错误重试
5. 添加指标监控

## 📦 依赖说明

当前只依赖：
- Spring Boot Web（用于启动和HTTP接口）
- Lombok（简化代码）

不依赖：
- ❌ MySQL
- ❌ Redis
- ❌ 消息队列
- ❌ 配置中心
- ❌ 其他复杂组件

## Java 8+ 新特性说明

本模块在 Controller 与 Service 中使用了 **var**、**List.of** / **Map.of**、**Optional**、**Stream**、**ConcurrentHashMap**、**computeIfAbsent**、**Duration** 等特性，并在代码中保留了「对比学习」的旧写法注释。各特性含义与选用原因详见：[JAVA_FEATURES.md](JAVA_FEATURES.md)。

## 💡 使用场景

- 本地开发和测试
- 快速验证本地模型调用
- 独立部署和运行
- 作为其他项目的依赖模块

---

**注意**：当前为框架结构，调用逻辑待实现。

