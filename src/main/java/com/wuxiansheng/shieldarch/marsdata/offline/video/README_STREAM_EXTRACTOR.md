# 内存流式视频抽帧模块

## 📋 概述

基于 **JavaCV Platform** 实现的内存流式视频抽帧模块，核心特性：

1. ✅ **不落盘**：直接从 S3 URL 读取，视频数据不写入本地文件
2. ✅ **内存处理**：所有处理在内存中完成，支持跳帧采样
3. ✅ **Base64 输出**：直接转换为 Base64，准备发送给 LLM
4. ✅ **跳帧采样**：支持低帧率采样（如 1fps、0.5fps），适合定价业务

## 🏗️ 架构设计

```
S3 预签名 URL
    ↓
FFmpegFrameGrabber (JavaCV)
    ↓
跳帧采样 (内存中)
    ↓
BufferedImage → Base64
    ↓
FrameProcessor 回调
    ↓
LLM 编排器
```

## 📦 核心类

### 1. `JavaCVStreamVideoExtractor`
核心抽帧器，提供流式处理能力。

**关键方法：**
- `processVideoStream(String s3Url, ...)` - 从 S3 URL 处理（推荐，不落盘）
- `processVideoStream(InputStream, ...)` - 从流处理（会产生临时文件）

### 2. `StreamVideoExtractorAdapter`
适配器类，简化调用，封装 S3 客户端操作。

### 3. `FrameProcessor`
帧处理回调接口，每提取一帧时调用。

## 🚀 快速开始

### 基础使用

```java
// 1. 创建提取器
JavaCVStreamVideoExtractor extractor = new JavaCVStreamVideoExtractor(1280, 720, 0);

// 2. 配置抽帧选项（1fps，即每秒1帧）
FrameExtractOptions options = new FrameExtractOptions();
options.setIntervalSeconds(1.0);
options.setMaxFrames(100);

// 3. 从 S3 URL 处理
String s3Url = s3Client.getFileURL(bucketName, objectKey);
extractor.processVideoStream(s3Url, options, (frameIndex, timestamp, image, base64) -> {
    // 4. 处理每一帧（直接发送给 LLM）
    log.info("帧 {}: timestamp={}s, base64长度={}", frameIndex, timestamp, base64.length());
    // orchestrator.dispatch(base64);
});

extractor.close();
```

### 使用适配器（推荐）

```java
// 1. 创建适配器
StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(s3Client);

// 2. 配置选项
FrameExtractOptions options = new FrameExtractOptions();
options.setIntervalSeconds(1.0);  // 1fps
options.setMaxFrames(50);

// 3. 处理视频
adapter.processFromS3Stream(bucketName, objectKey, "mp4", options, 
    (frameIndex, timestamp, image, base64) -> {
        // 发送给 LLM
        llmClient.processImage(base64);
    });

adapter.close();
```

### 定价业务场景（低帧率）

```java
StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(
    s3Client, 1280, 720);  // 降低分辨率

FrameExtractOptions options = new FrameExtractOptions();
options.setIntervalSeconds(2.0);  // 每2秒1帧（0.5fps）
options.setMaxFrames(30);  // 最多30帧（1分钟视频）

adapter.processFromS3Stream(bucketName, objectKey, "mp4", options,
    (frameIndex, timestamp, image, base64) -> {
        // 价格识别
        priceRecognizer.recognize(base64);
    });
```

## ⚙️ 配置说明

### FrameExtractOptions 参数

| 参数 | 说明 | 默认值 | 示例 |
|------|------|--------|------|
| `intervalSeconds` | 抽帧间隔（秒） | - | 1.0（1fps） |
| `maxFrames` | 最大帧数（0=无限制） | 0 | 100 |
| `startMillis` | 起始时间（毫秒） | 0 | 5000 |
| `quality` | 图片质量 | - | - |
| `threads` | 线程数 | - | 4 |

### JavaCVStreamVideoExtractor 构造函数

```java
// 参数1: 最大宽度（0=不缩放）
// 参数2: 最大高度（0=不缩放）
// 参数3: 异步处理线程数（0=同步）
JavaCVStreamVideoExtractor extractor = new JavaCVStreamVideoExtractor(1280, 720, 0);
```

## 🔑 核心优势

### 1. 真正的"不落盘"
- 使用 S3 预签名 URL，FFmpeg 直接从网络读取
- 视频数据不写入本地文件系统
- 减少磁盘 IO，提升性能

### 2. 跳帧采样策略
- 不处理每一帧，只采样需要的帧
- 例如：30fps 视频，1fps 采样 = 每30帧取1帧
- 大幅减少 CPU 和内存占用

### 3. 内存优化
- 支持降低分辨率（如 1280x720）
- 及时释放帧数据
- Base64 编码后立即处理，不累积

### 4. 异步处理支持
- 可配置异步线程池
- 帧处理不阻塞主流程
- 适合高并发场景

## 📊 性能对比

| 方案 | 磁盘IO | 内存占用 | 处理速度 | 适用场景 |
|------|--------|----------|----------|----------|
| **当前方案**（ProcessBuilder） | 高（需下载） | 中 | 快 | 简单场景 |
| **流式方案**（JavaCV URL） | 无 | 低 | 快 | **推荐** |
| **流式方案**（JavaCV InputStream） | 中（临时文件） | 中 | 中 | 不推荐 |

## ⚠️ 注意事项

1. **S3 URL 方式（推荐）**
   - 使用预签名 URL，FFmpeg 可直接读取
   - 真正的"不落盘"
   - 需要 S3 支持 HTTP Range 请求

2. **InputStream 方式（不推荐）**
   - FFmpegFrameGrabber 不支持直接从 InputStream 读取
   - 需要先写入临时文件
   - 会产生磁盘 IO

3. **内存管理**
   - 及时处理帧数据，避免累积
   - 使用跳帧采样，减少内存占用
   - 降低分辨率可进一步减少内存

4. **错误处理**
   - 网络流可能中断，需要重试机制
   - 视频格式不支持时会有异常
   - 建议添加超时控制

## 🔄 迁移指南

### 从 VideoExtractor 迁移

**旧代码：**
```java
VideoExtractor extractor = new VideoExtractor(ffmpegPath, ffprobePath, outputDir, ...);
List<String> framePaths = extractor.extractFrames(localVideoPath, options);
```

**新代码：**
```java
StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(s3Client);
adapter.processFromS3Stream(bucketName, objectKey, "mp4", options, 
    (frameIndex, timestamp, image, base64) -> {
        // 直接处理 base64，无需本地文件路径
    });
```

## 📝 示例代码

完整示例请参考：
- `StreamVideoExtractorExample.java` - 各种使用场景示例

## 🔗 相关文档

- [JavaCV 官方文档](https://github.com/bytedeco/javacv)
- [FFmpeg 文档](https://ffmpeg.org/documentation.html)

