# VideoListTask 定时任务说明

## 📋 概述

`VideoListTask` 是一个定时调度任务，每半小时执行一次，从 S3 指定路径获取视频列表。

**核心功能**：
1. ✅ 定时扫描 S3 目录
2. ✅ 使用 Redis 记录已处理文件（去重）
3. ✅ 识别新增文件
4. ✅ 发送到 MQ

## ⚙️ 配置

### 配置文件（application.yml）

```yaml
scheduler:
  video-list:
    enabled: true              # 是否启用任务（默认：true）
    bucket: your-bucket-name    # S3 桶名称（必填）
    prefix: videos/2024/        # S3 路径前缀（必填）
    mq-topic: ocr_video_capture  # MQ Topic（默认：ocr_video_capture）
    redis-ttl-days: 30          # Redis 已处理文件记录过期时间（天，默认：30）
```

### 环境变量配置

也可以通过环境变量配置：

```bash
export SCHEDULER_VIDEO_LIST_ENABLED=true
export SCHEDULER_VIDEO_LIST_BUCKET=your-bucket-name
export SCHEDULER_VIDEO_LIST_PREFIX=videos/2024/
export SCHEDULER_VIDEO_LIST_MQ_TOPIC=ocr_video_capture
export SCHEDULER_VIDEO_LIST_REDIS_TTL_DAYS=30
```

## 🕐 执行频率

- **Cron 表达式**: `0 0,30 * * * ?`
- **执行时间**: 每小时的 0 分和 30 分（例如：10:00, 10:30, 11:00, 11:30...）
- **执行间隔**: 30 分钟

## 🔒 分布式锁

- **锁键名**: `video_list_task_lock`
- **锁过期时间**: 25 分钟
- **作用**: 确保在分布式环境下，同一时间只有一个实例执行任务

## 📝 功能说明

### 1. 定时扫描 S3 目录

任务会从 S3 指定路径获取视频文件列表，支持：

- **直接列举**: 如果指定前缀下没有子目录，直接列举该前缀下的视频文件
- **递归子目录**: 如果指定前缀下有子目录，会遍历每个子目录获取视频文件

### 2. Redis 去重（已处理文件记录）

- 使用 **Redis Set** 存储已处理的文件列表
- Redis 键名格式: `video_list:processed:{bucket}:{prefix}`
- 自动设置过期时间（默认 30 天），防止 Redis 数据无限增长
- 支持分布式环境，多个实例共享已处理文件记录

### 3. 识别新增文件

- 对比当前扫描的文件和 Redis 中已处理的文件
- 只处理新增的文件，避免重复处理
- 自动识别新上传的视频文件

### 4. 发送到 MQ

- 将新增文件发送到配置的 MQ Topic
- 消息格式为 JSON：
  ```json
  {
    "videoKey": "videos/2024/01/video.mp4",
    "bucket": "video-storage",
    "timestamp": 1704067200000
  }
  ```
- 支持批量发送，记录成功/失败统计

### 5. 视频文件过滤

自动过滤以下格式的视频文件：

- `.mp4`
- `.avi`
- `.mov`
- `.mkv`
- `.flv`
- `.webm`

### 6. 日志输出

任务执行时会输出：

- 执行开始/结束日志
- 扫描到的文件总数
- Redis 中已处理文件数
- 识别到的新增文件数
- MQ 发送成功/失败统计
- 新增文件列表（前10个，避免日志过长）

## 🚀 使用示例

### 基本配置

```yaml
scheduler:
  video-list:
    enabled: true
    bucket: video-storage
    prefix: videos/2024/01/
```

### 禁用任务

```yaml
scheduler:
  video-list:
    enabled: false
```

### 动态路径（使用环境变量）

```bash
# 根据日期动态设置路径
export SCHEDULER_VIDEO_LIST_PREFIX="videos/$(date +%Y/%m)/"
```

## 🔧 工作流程

```
1. 定时触发（每30分钟）
   ↓
2. 扫描 S3 目录，获取所有视频文件
   ↓
3. 从 Redis 读取已处理文件列表
   ↓
4. 对比识别新增文件
   ↓
5. 发送新增文件到 MQ
   ↓
6. 将新增文件标记为已处理（写入 Redis）
   ↓
7. 完成
```

## 🔧 扩展功能

如果需要自定义处理逻辑，可以修改 `sendToMQ()` 方法：

```java
// 示例：自定义消息格式
private String buildMQMessage(String videoKey) {
    // 可以添加更多字段
    VideoMQMessage message = new VideoMQMessage();
    message.setVideoKey(videoKey);
    message.setBucket(bucket);
    message.setTimestamp(System.currentTimeMillis());
    // 添加自定义字段...
    return objectMapper.writeValueAsString(message);
}
```

## 📊 监控指标

任务执行时会自动上报以下指标：

- `scheduler_task`: 任务执行计数
- `scheduler_task_duration`: 任务执行时长（毫秒）

## ⚠️ 注意事项

1. **配置必填项**
   - `bucket`: S3 桶名称必须配置
   - `prefix`: S3 路径前缀必须配置

2. **依赖组件**
   - **S3Client**: 必须配置，用于扫描 S3 目录
   - **RedissonClient**: 必须配置，用于 Redis 去重
   - **MQ Producer**: 必须配置，用于发送消息到 MQ

3. **Redis 配置**
   - 确保 Redis 连接正常
   - 已处理文件记录会自动过期（默认 30 天）
   - Redis 键名格式: `video_list:processed:{bucket}:{prefix}`

4. **MQ 配置**
   - 确保 MQ Producer 已初始化
   - 确保有发送消息到指定 Topic 的权限
   - 消息发送失败会记录日志，但不会中断任务

5. **性能考虑**
   - 如果视频文件数量很大，建议限制前缀范围
   - Redis Set 操作是 O(1) 时间复杂度，性能良好
   - MQ 发送是同步的，如果文件很多可能需要较长时间

6. **错误处理**
   - 任务执行失败会记录错误日志，但不会中断调度
   - Redis 操作失败会降级处理（继续执行，但可能重复处理）
   - MQ 发送失败会记录日志，但已处理文件仍会标记（避免重复发送）
   - 建议配置告警监控任务执行状态

## 🔍 日志示例

```
[VideoListTask] 开始执行，获取 S3 视频列表
[VideoListTask] 请求参数 | bucket=video-storage | prefix=videos/2024/01/
[VideoListTask] 发现子目录: 3 个
[VideoListTask] 处理子目录: videos/2024/01/day1/
[VideoListTask] 处理子目录: videos/2024/01/day2/
[VideoListTask] 处理子目录: videos/2024/01/day3/
[VideoListTask] 扫描完成，共找到 150 个视频文件
[VideoListTask] Redis 中已处理文件数: 120
[VideoListTask] 识别到新增文件: 30 个
[VideoListTask] 发送到 MQ 成功: 30 个文件
[VideoListTask] 已标记 30 个文件为已处理
[VideoListTask] 执行完成 | 总文件数=150 | 已处理数=120 | 新增数=30 | 发送MQ数=30
[VideoListTask] 新增文件列表（前10个）:
videos/2024/01/day1/video31.mp4
videos/2024/01/day1/video32.mp4
...
```

## 🔗 相关代码

- `ListStage.java` - 参考的视频列表获取实现
- `Scheduler.java` - 定时任务调度器
- `SchedulerConfig.java` - 任务注册配置

