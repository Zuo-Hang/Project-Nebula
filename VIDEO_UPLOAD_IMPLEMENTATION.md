# 视频上传功能实现说明

## 📋 功能需求

实现前端上传视频，后端存储到S3，并发送MQ消息的完整流程。

## 🏗️ 架构决策

### 问题：MQ消息发送应该在Web层还是Service层？

**决策：在Service层发送MQ消息**

### 理由

1. **符合分层架构原则**
   - Controller层：只负责接收HTTP请求和返回响应
   - Service层：负责业务逻辑（上传、存储、消息发送）
   - 符合现有项目的架构模式：`Controller → Service → Orchestrator`

2. **职责清晰**
   - Controller层职责单一：参数验证、调用Service、返回响应
   - Service层职责明确：完整的业务流程（上传S3 + 发送MQ）

3. **便于测试和维护**
   - Service层可以独立测试，不依赖HTTP层
   - 业务逻辑集中，便于后续扩展（事务、重试、补偿等）

4. **易于扩展**
   - 如果后续需要添加事务保证（上传成功后再发送MQ）
   - 如果需要添加重试机制
   - 如果需要添加补偿逻辑（上传失败回滚）
   - 都可以在Service层统一处理

## 📁 实现文件

### 1. Controller层
- **文件**：`TaskController.java`
- **接口**：`POST /api/tasks/upload`
- **职责**：
  - 接收MultipartFile文件
  - 参数验证（文件非空、类型检查）
  - 调用Service层处理
  - 返回任务状态响应

### 2. Service层
- **文件**：`TaskService.java`
- **方法**：`uploadVideo()`
- **职责**：
  - 保存文件到临时目录
  - 上传到S3存储
  - 发送MQ消息
  - 清理临时文件
  - 构建任务上下文

### 3. DTO层
- **文件**：`TaskStatusResponse.java`
- **新增字段**：`videoKey`（S3路径）

## 🔄 执行流程

```
前端上传视频
    ↓
Controller层（TaskController.uploadVideo）
    ├─ 接收MultipartFile
    ├─ 参数验证
    └─ 调用Service层
        ↓
Service层（TaskService.uploadVideo）
    ├─ 1. 保存文件到临时目录
    ├─ 2. 上传到S3存储（S3Client.uploadFile）
    ├─ 3. 发送MQ消息（MQProducer.send）
    ├─ 4. 构建任务上下文
    └─ 5. 清理临时文件
        ↓
返回TaskStatusResponse（包含taskId和videoKey）
```

## 📝 配置项

在`application.yml`中添加以下配置：

```yaml
orchestrator:
  upload:
    # S3存储桶名称
    s3-bucket: ai-orchestrator
    # S3存储路径前缀
    s3-prefix: uploads/videos/
    # MQ Topic
    mq-topic: ocr_video_capture
    # 临时文件存储目录
    temp-dir: ${java.io.tmpdir}/video_uploads
```

## 🔧 使用示例

### 前端调用

```javascript
const formData = new FormData();
formData.append('file', videoFile);
formData.append('linkName', 'test-link');
formData.append('submitDate', '2024-01-01');

fetch('/api/tasks/upload', {
  method: 'POST',
  body: formData
})
.then(response => response.json())
.then(data => {
  console.log('上传成功:', data);
  // data.taskId - 任务ID
  // data.videoKey - S3路径
  // data.status - 任务状态
});
```

### cURL调用

```bash
curl -X POST http://localhost:8080/api/tasks/upload \
  -F "file=@/path/to/video.mp4" \
  -F "linkName=test-link" \
  -F "submitDate=2024-01-01"
```

## 📊 监控指标

视频上传功能会自动上报以下指标到Prometheus。**指标上报采用分层架构**：

### 架构设计：工具类内部上报指标

**设计原则**：指标上报应该在工具类内部完成，而不是在调用方。

- ✅ **S3Client**：内部上报S3相关指标（上传、下载等）
- ✅ **MQProducer**：内部上报MQ相关指标（发送成功/失败、耗时）
- ✅ **Service层**：只上报业务层面的指标（整体上传成功/失败、业务耗时）

**优势**：
1. **职责清晰**：工具类负责自己的监控
2. **复用性好**：任何地方调用工具类都会自动上报指标
3. **不会遗漏**：无论从哪里调用，都会上报
4. **符合单一职责原则**：工具类既负责功能实现，也负责自己的监控

### 1. 业务层指标（Service层上报）

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `video_upload_total` | Counter | 视频上传总数（成功/失败） | `task_id`, `status`, `bucket` |
| `video_upload_duration` | Histogram | 视频上传总耗时（毫秒） | `task_id`, `status`, `bucket` |
| `video_upload_file_size` | Gauge | 上传文件大小（字节） | `task_id`, `status`, `bucket`, `file_size_range` |

### 2. S3工具类指标（S3Client内部上报）

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `s3_upload_total` | Counter | S3上传总数（成功/失败） | `bucket`, `status`, `error_type`, `retry_count` |
| `s3_upload_duration` | Histogram | S3上传耗时（毫秒） | `bucket`, `status`, `error_type` |
| `s3_upload_file_size` | Gauge | S3上传文件大小（字节） | `bucket`, `status`, `file_size_range` |

**特点**：
- 自动记录重试次数
- 自动记录错误类型
- 自动记录文件大小分布
- 任何调用S3Client.uploadFile()的地方都会上报

### 3. MQ工具类指标（MQProducer内部上报）

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `mq_producer_send_total` | Counter | MQ发送总数（成功/失败） | `topic`, `status`, `error_type` |
| `mq_producer_send_duration` | Histogram | MQ发送耗时（毫秒） | `topic`, `status`, `error_type` |

**特点**：
- 自动记录发送成功/失败
- 自动记录发送耗时
- 自动记录错误类型
- 任何调用MQProducer.send()的地方都会上报

### 指标标签说明

- `status`: `success` 或 `failed`
- `bucket`: S3存储桶名称
- `topic`: MQ主题名称
- `task_id`: 任务ID
- `file_size_range`: 文件大小范围（`0-1MB`, `1-10MB`, `10-100MB`, `100-500MB`, `500MB+`）
- `error_type`: 错误类型（仅失败时）

### Grafana查询示例

```promql
# 业务层：上传成功率
sum(rate(video_upload_total{status="success"}[5m])) / sum(rate(video_upload_total[5m])) * 100

# 业务层：平均上传耗时
rate(video_upload_duration_sum[5m]) / rate(video_upload_duration_count[5m])

# 业务层：文件大小分布
sum by (file_size_range) (video_upload_file_size)

# S3工具层：S3上传失败率
sum(rate(s3_upload_total{status="failed"}[5m])) / sum(rate(s3_upload_total[5m])) * 100

# S3工具层：S3上传平均耗时
rate(s3_upload_duration_sum[5m]) / rate(s3_upload_duration_count[5m])

# S3工具层：重试次数分布
sum by (retry_count) (s3_upload_total)

# MQ工具层：MQ发送失败率
sum(rate(mq_producer_send_total{status="failed"}[5m])) / sum(rate(mq_producer_send_total[5m])) * 100

# MQ工具层：MQ发送平均耗时
rate(mq_producer_send_duration_sum[5m]) / rate(mq_producer_send_duration_count[5m])
```

### 指标上报架构

```
Service层（TaskService）
  ├─ 上报业务指标：video_upload_total, video_upload_duration
  │
  ├─ 调用 S3Client.uploadFile()
  │   └─ S3Client内部上报：s3_upload_total, s3_upload_duration
  │
  └─ 调用 MQProducer.send()
      └─ MQProducer内部上报：mq_producer_send_total, mq_producer_send_duration
```

**优势**：
- 工具类指标可以复用：任何地方调用S3Client或MQProducer都会自动上报
- 业务层指标聚焦业务：只关注业务层面的成功率和耗时
- 职责清晰：工具类负责自己的监控，业务层负责业务监控

## ⚠️ 注意事项

1. **文件大小限制**
   - 需要在`application.yml`中配置Spring Boot的文件上传大小限制：
   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 500MB
         max-request-size: 500MB
   ```

2. **临时文件清理**
   - 代码中已实现临时文件清理逻辑
   - 建议定期清理临时目录，避免磁盘空间不足

3. **错误处理**
   - 上传失败时会抛出异常
   - MQ发送失败会抛出异常（确保数据一致性）
   - 临时文件会在finally块中清理
   - **失败时会自动上报失败指标**，便于监控和告警

4. **事务保证**
   - 当前实现：先上传S3，成功后再发送MQ
   - 如果MQ发送失败，S3文件已上传（需要后续补偿机制）
   - 如果需要严格的事务保证，可以考虑：
     - 使用事务性消息（RocketMQ支持）
     - 或者先发送MQ，消费者端验证S3文件存在

5. **监控指标**
   - 所有指标都会自动上报到Prometheus
   - 建议在Grafana中配置告警规则：
     - 上传失败率 > 5%
     - 平均上传耗时 > 30秒
     - S3上传失败率 > 1%
     - MQ发送失败率 > 1%

## 🔄 后续优化建议

1. **异步处理**
   - 可以将上传和MQ发送改为异步处理，提高响应速度
   - 使用`@Async`注解或消息队列

2. **断点续传**
   - 对于大文件，可以实现分片上传和断点续传

3. **进度反馈**
   - 可以通过WebSocket或SSE实时反馈上传进度

4. **文件校验**
   - 添加文件类型、大小、格式校验
   - 添加视频格式验证（MP4、AVI等）

5. **事务性消息**
   - 使用RocketMQ的事务消息，确保S3上传和MQ发送的一致性

## 📊 架构对比

### 方案1：Controller层发送MQ（不推荐）

```java
@PostMapping("/upload")
public ResponseEntity<?> upload(MultipartFile file) {
    // 上传到S3
    String videoKey = s3Client.upload(...);
    
    // Controller层直接发送MQ（不推荐）
    mqProducer.send(topic, message);
    
    return ResponseEntity.ok();
}
```

**缺点**：
- Controller层职责过重
- 业务逻辑混在Controller中
- 难以测试和维护
- 不符合分层架构原则

### 方案2：Service层发送MQ（推荐，已实现）

```java
// Controller层
@PostMapping("/upload")
public ResponseEntity<?> upload(MultipartFile file) {
    TaskStatusResponse response = taskService.uploadVideo(file, ...);
    return ResponseEntity.ok(response);
}

// Service层
public TaskStatusResponse uploadVideo(...) {
    // 上传到S3
    s3Client.uploadFile(...);
    
    // Service层发送MQ（推荐）
    mqProducer.send(topic, message);
    
    return response;
}
```

**优点**：
- 职责清晰，符合分层架构
- 便于测试和维护
- 易于扩展（事务、重试等）

## ✅ 总结

**MQ消息发送应该在Service层处理**，这样：
1. 符合项目的分层架构
2. 职责清晰，便于维护
3. 易于扩展和测试
4. 符合单一职责原则
