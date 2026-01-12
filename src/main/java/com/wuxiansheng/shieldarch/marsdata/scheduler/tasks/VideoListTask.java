package com.wuxiansheng.shieldarch.marsdata.scheduler.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.mq.Producer;
import com.wuxiansheng.shieldarch.marsdata.scheduler.LockedTask;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时获取 S3 视频列表任务
 * 
 * 每半小时执行一次，从 S3 指定路径获取视频列表
 */
@Slf4j
@Component
public class VideoListTask implements LockedTask {

    /**
     * Cron 表达式：每半小时执行一次（0分和30分）
     */
    private static final String CRON_EXPRESSION = "0 0,30 * * * ?";
    
    /**
     * 分布式锁键名
     */
    private static final String LOCK_KEY = "video_list_task_lock";
    
    /**
     * 分布式锁过期时间（25分钟，确保在下次执行前释放）
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(25);

    /**
     * Redis 已处理文件集合的键前缀
     */
    private static final String REDIS_PROCESSED_KEY_PREFIX = "video_list:processed:";

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired(required = false)
    private Producer mqProducer;

    /**
     * S3 桶名称（可配置）
     */
    @Value("${scheduler.video-list.bucket:}")
    private String bucket;

    /**
     * S3 路径前缀（可配置）
     */
    @Value("${scheduler.video-list.prefix:}")
    private String prefix;

    /**
     * 是否启用任务（可配置）
     */
    @Value("${scheduler.video-list.enabled:true}")
    private boolean enabled;

    /**
     * MQ Topic（可配置）
     */
    @Value("${scheduler.video-list.mq-topic:ocr_video_capture}")
    private String mqTopic;

    /**
     * Redis 已处理文件集合的过期时间（天）
     */
    @Value("${scheduler.video-list.redis-ttl-days:30}")
    private int redisTtlDays;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "VideoListTask";
    }

    @Override
    public void execute() throws Exception {
        if (!enabled) {
            log.debug("[VideoListTask] 任务已禁用，跳过执行");
            return;
        }

        log.info("[VideoListTask] 开始执行，获取 S3 视频列表");

        // 1. 验证配置
        if (s3Client == null) {
            throw new Exception("[VideoListTask] S3Client 未配置");
        }

        if (bucket == null || bucket.isEmpty()) {
            throw new Exception("[VideoListTask] bucket 未配置，请设置 scheduler.video-list.bucket");
        }

        if (prefix == null || prefix.isEmpty()) {
            throw new Exception("[VideoListTask] prefix 未配置，请设置 scheduler.video-list.prefix");
        }

        // 2. 确保 prefix 以 / 结尾
        String prefixValue = prefix;
        if (!prefixValue.endsWith("/")) {
            prefixValue += "/";
        }

        log.info("[VideoListTask] 请求参数 | bucket={} | prefix={}", bucket, prefixValue);

        // 3. 获取视频列表（参考 ListStage 的实现）
        List<String> allVideos = getVideoList(bucket, prefixValue);
        log.info("[VideoListTask] 扫描完成，共找到 {} 个视频文件", allVideos.size());

        // 4. 从 Redis 获取已处理文件列表（去重）
        Set<String> processedVideos = getProcessedVideos(bucket, prefixValue);
        log.info("[VideoListTask] Redis 中已处理文件数: {}", processedVideos.size());

        // 5. 识别新增文件
        List<String> newVideos = identifyNewVideos(allVideos, processedVideos);
        log.info("[VideoListTask] 识别到新增文件: {} 个", newVideos.size());

        if (newVideos.isEmpty()) {
            log.info("[VideoListTask] 没有新增文件，任务完成");
            return;
        }

        // 6. 发送新增文件到 MQ
        int sentCount = sendToMQ(newVideos);
        log.info("[VideoListTask] 发送到 MQ 成功: {} 个文件", sentCount);

        // 7. 将新增文件标记为已处理（写入 Redis）
        markAsProcessed(newVideos, bucket, prefixValue);
        log.info("[VideoListTask] 已标记 {} 个文件为已处理", newVideos.size());

        // 8. 记录结果
        log.info("[VideoListTask] 执行完成 | 总文件数={} | 已处理数={} | 新增数={} | 发送MQ数={}", 
            allVideos.size(), processedVideos.size(), newVideos.size(), sentCount);
        
        if (!newVideos.isEmpty()) {
            log.info("[VideoListTask] 新增文件列表（前10个）:\n{}", 
                newVideos.stream().limit(10).collect(Collectors.joining("\n")));
            if (newVideos.size() > 10) {
                log.info("[VideoListTask] ... 还有 {} 个新增文件未显示", newVideos.size() - 10);
            }
        }
    }

    /**
     * 获取视频列表（参考 ListStage 的实现）
     * 
     * @param bucketName S3 桶名称
     * @param prefixValue S3 路径前缀
     * @return 视频文件列表
     */
    private List<String> getVideoList(String bucketName, String prefixValue) throws Exception {
        // 1. 列出目录
        List<String> dirs = s3Client.listDirectories(bucketName, prefixValue);
        log.info("[VideoListTask] 发现子目录: {} 个", dirs.size());
        if (!dirs.isEmpty()) {
            log.debug("[VideoListTask] 子目录清单: {}", String.join(", ", dirs));
        }

        List<String> videos = new ArrayList<>();
        
        // 2. 如果没有子目录，直接列举当前前缀下的文件
        if (dirs.isEmpty()) {
            log.info("[VideoListTask] 无子目录，直接列举当前前缀下文件(非递归)");
            List<String> files = s3Client.listFiles(bucketName, prefixValue);
            videos.addAll(filterVideosByExt(files));
        } else {
            // 3. 如果有子目录，遍历每个子目录
            for (String d : dirs) {
                String dirPrefix = prefixValue;
                if (!dirPrefix.isEmpty() && !dirPrefix.endsWith("/")) {
                    dirPrefix += "/";
                }
                dirPrefix += d + "/";
                
                log.debug("[VideoListTask] 处理子目录: {}", dirPrefix);
                List<String> files = s3Client.listFiles(bucketName, dirPrefix);
                videos.addAll(filterVideosByExt(files));
            }
        }

        return videos;
    }

    /**
     * 过滤视频文件（参考 ListStage 的实现）
     * 
     * @param keys 文件键列表
     * @return 视频文件列表
     */
    private List<String> filterVideosByExt(List<String> keys) {
        // 视频扩展名列表
        Set<String> videoExtensions = new HashSet<>();
        videoExtensions.add(".mp4");
        videoExtensions.add(".avi");
        videoExtensions.add(".mov");
        videoExtensions.add(".mkv");
        videoExtensions.add(".flv");
        videoExtensions.add(".webm");

        return keys.stream()
            .filter(key -> {
                String ext = getFileExtension(key).toLowerCase();
                return videoExtensions.contains(ext);
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取文件扩展名
     * 
     * @param filename 文件名
     * @return 扩展名（包含点号）
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            // 移除查询参数（如果有）
            String ext = filename.substring(lastDot);
            int queryIndex = ext.indexOf('?');
            if (queryIndex > 0) {
                ext = ext.substring(0, queryIndex);
            }
            return ext;
        }
        return "";
    }

    @Override
    public String getSchedule() {
        return CRON_EXPRESSION;
    }

    @Override
    public String getLockKey() {
        return LOCK_KEY;
    }

    @Override
    public Duration getLockTTL() {
        return LOCK_TTL;
    }

    /**
     * 从 Redis 获取已处理的文件列表
     * 
     * @param bucketName S3 桶名称
     * @param prefixValue S3 路径前缀
     * @return 已处理文件集合
     */
    private Set<String> getProcessedVideos(String bucketName, String prefixValue) {
        if (redissonClient == null) {
            log.warn("[VideoListTask] RedissonClient 未配置，无法获取已处理文件列表，将处理所有文件");
            return new HashSet<>();
        }

        try {
            String redisKey = buildRedisKey(bucketName, prefixValue);
            RSet<String> processedSet = redissonClient.getSet(redisKey);
            
            Set<String> processed = new HashSet<>(processedSet.readAll());
            log.debug("[VideoListTask] 从 Redis 读取已处理文件: key={}, count={}", redisKey, processed.size());
            
            return processed;
        } catch (Exception e) {
            log.error("[VideoListTask] 从 Redis 获取已处理文件列表失败", e);
            return new HashSet<>();
        }
    }

    /**
     * 识别新增文件（对比当前文件和已处理文件）
     * 
     * @param allVideos 所有视频文件列表
     * @param processedVideos 已处理文件集合
     * @return 新增文件列表
     */
    private List<String> identifyNewVideos(List<String> allVideos, Set<String> processedVideos) {
        return allVideos.stream()
            .filter(video -> !processedVideos.contains(video))
            .collect(Collectors.toList());
    }

    /**
     * 发送新增文件到 MQ
     * 
     * @param newVideos 新增文件列表
     * @return 成功发送的数量
     */
    private int sendToMQ(List<String> newVideos) {
        if (mqProducer == null) {
            log.warn("[VideoListTask] MQ Producer 未配置，跳过发送到 MQ");
            return 0;
        }

        if (mqTopic == null || mqTopic.isEmpty()) {
            log.warn("[VideoListTask] MQ Topic 未配置，跳过发送到 MQ");
            return 0;
        }

        int successCount = 0;
        int failCount = 0;

        for (String videoKey : newVideos) {
            try {
                // 构建消息体（JSON 格式）
                String message = buildMQMessage(videoKey);
                
                // 发送到 MQ
                boolean sent = mqProducer.send(mqTopic, message);
                
                if (sent) {
                    successCount++;
                    log.debug("[VideoListTask] 发送到 MQ 成功: topic={}, video={}", mqTopic, videoKey);
                } else {
                    failCount++;
                    log.warn("[VideoListTask] 发送到 MQ 失败: topic={}, video={}", mqTopic, videoKey);
                }
            } catch (Exception e) {
                failCount++;
                log.error("[VideoListTask] 发送到 MQ 异常: video={}, error={}", videoKey, e.getMessage(), e);
            }
        }

        if (failCount > 0) {
            log.warn("[VideoListTask] MQ 发送统计: 成功={}, 失败={}", successCount, failCount);
        }

        return successCount;
    }

    /**
     * 构建 MQ 消息体
     * 
     * @param videoKey 视频文件键
     * @return JSON 格式的消息
     */
    private String buildMQMessage(String videoKey) {
        try {
            // 构建消息对象
            VideoMQMessage message = new VideoMQMessage();
            message.setVideoKey(videoKey);
            message.setBucket(bucket);
            message.setTimestamp(System.currentTimeMillis());
            
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("[VideoListTask] 构建 MQ 消息失败: videoKey={}", videoKey, e);
            // 如果 JSON 序列化失败，返回简单的字符串
            return videoKey;
        }
    }

    /**
     * 将文件标记为已处理（写入 Redis）
     * 
     * @param videos 文件列表
     * @param bucketName S3 桶名称
     * @param prefixValue S3 路径前缀
     */
    private void markAsProcessed(List<String> videos, String bucketName, String prefixValue) {
        if (redissonClient == null) {
            log.warn("[VideoListTask] RedissonClient 未配置，无法标记已处理文件");
            return;
        }

        if (videos.isEmpty()) {
            return;
        }

        try {
            String redisKey = buildRedisKey(bucketName, prefixValue);
            RSet<String> processedSet = redissonClient.getSet(redisKey);
            
            // 批量添加
            for (String video : videos) {
                processedSet.add(video);
            }
            
            // 设置过期时间（防止 Redis 中数据无限增长）
            long ttlSeconds = redisTtlDays * 24L * 3600L;
            processedSet.expire(java.time.Duration.ofSeconds(ttlSeconds));
            
            log.debug("[VideoListTask] 已标记 {} 个文件为已处理: key={}", videos.size(), redisKey);
        } catch (Exception e) {
            log.error("[VideoListTask] 标记已处理文件失败", e);
        }
    }

    /**
     * 构建 Redis 键名
     * 
     * @param bucketName S3 桶名称
     * @param prefixValue S3 路径前缀
     * @return Redis 键名
     */
    private String buildRedisKey(String bucketName, String prefixValue) {
        // 使用 bucket 和 prefix 构建唯一的键名
        // 例如: video_list:processed:bucket-name:prefix-path
        String normalizedPrefix = prefixValue.replace("/", ":").replaceAll("[:]+", ":");
        if (normalizedPrefix.endsWith(":")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return REDIS_PROCESSED_KEY_PREFIX + bucketName + ":" + normalizedPrefix;
    }

    /**
     * MQ 消息对象
     * 使用 Lombok 风格的 getter/setter（Jackson 会自动识别）
     */
    @lombok.Data
    private static class VideoMQMessage {
        private String videoKey;
        private String bucket;
        private long timestamp;
    }
}

