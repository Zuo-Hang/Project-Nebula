package com.wuxiansheng.shieldarch.orchestrator.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.stepexecutors.io.S3Client;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * S3扫描触发器
 * 对应旧项目的 pipeline.stages.ListStage 和 scheduler.tasks.VideoListTask
 * 
 * 定时扫描S3目录，发现新视频文件，发送到MQ
 */
@Slf4j
@Component
public class S3ScannerTrigger {

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired(required = false)
    private MQProducer mqProducer;

    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * S3 桶名称（可配置）
     */
    @Value("${scheduler.s3-scanner.bucket:}")
    private String bucket;

    /**
     * S3 路径前缀（可配置）
     */
    @Value("${scheduler.s3-scanner.prefix:}")
    private String prefix;

    /**
     * 是否启用任务（可配置）
     */
    @Value("${scheduler.s3-scanner.enabled:true}")
    private boolean enabled;

    /**
     * MQ Topic（可配置）
     */
    @Value("${scheduler.s3-scanner.mq-topic:ocr_video_capture}")
    private String mqTopic;

    /**
     * Redis 已处理文件集合的过期时间（天）
     */
    @Value("${scheduler.s3-scanner.redis-ttl-days:30}")
    private int redisTtlDays;

    /**
     * Redis 已处理文件集合的键前缀
     */
    private static final String REDIS_PROCESSED_KEY_PREFIX = "s3_scanner:processed:";

    /**
     * 定时扫描任务（每半小时执行一次）
     * Cron表达式：0分和30分执行
     */
    @Scheduled(cron = "0 0,30 * * * ?")
    public void scanAndSend() {
        if (!enabled) {
            log.debug("[S3ScannerTrigger] 任务已禁用，跳过执行");
            return;
        }

        log.info("[S3ScannerTrigger] 开始执行，获取 S3 视频列表");

        try {
            // 1. 验证配置
            if (s3Client == null) {
                throw new Exception("[S3ScannerTrigger] S3Client 未配置");
            }

            if (bucket == null || bucket.isEmpty()) {
                throw new Exception("[S3ScannerTrigger] bucket 未配置，请设置 scheduler.s3-scanner.bucket");
            }

            if (prefix == null || prefix.isEmpty()) {
                throw new Exception("[S3ScannerTrigger] prefix 未配置，请设置 scheduler.s3-scanner.prefix");
            }

            // 2. 确保 prefix 以 / 结尾
            String prefixValue = prefix;
            if (!prefixValue.endsWith("/")) {
                prefixValue += "/";
            }

            log.info("[S3ScannerTrigger] 请求参数 | bucket={} | prefix={}", bucket, prefixValue);

            // 3. 获取视频列表
            List<String> allVideos = getVideoList(bucket, prefixValue);
            log.info("[S3ScannerTrigger] 扫描完成，共找到 {} 个视频文件", allVideos.size());

            // 4. 从 Redis 获取已处理文件列表（去重）
            Set<String> processedVideos = getProcessedVideos(bucket, prefixValue);
            log.info("[S3ScannerTrigger] Redis 中已处理文件数: {}", processedVideos.size());

            // 5. 识别新增文件
            List<String> newVideos = identifyNewVideos(allVideos, processedVideos);
            log.info("[S3ScannerTrigger] 识别到新增文件: {} 个", newVideos.size());

            if (newVideos.isEmpty()) {
                log.info("[S3ScannerTrigger] 没有新增文件，任务完成");
                return;
            }

            // 6. 发送新增文件到 MQ
            int sentCount = sendToMQ(newVideos);
            log.info("[S3ScannerTrigger] 发送到 MQ 成功: {} 个文件", sentCount);

            // 7. 将新增文件标记为已处理（写入 Redis）
            markAsProcessed(newVideos, bucket, prefixValue);
            log.info("[S3ScannerTrigger] 已标记 {} 个文件为已处理", newVideos.size());

            // 8. 记录结果
            log.info("[S3ScannerTrigger] 执行完成 | 总文件数={} | 已处理数={} | 新增数={} | 发送MQ数={}", 
                allVideos.size(), processedVideos.size(), newVideos.size(), sentCount);
            
            // 上报指标
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("bucket", bucket);
                metricsClient.incrementCounter("s3_scanner_total_files", tags);
                tags.put("status", "new");
                metricsClient.incrementCounter("s3_scanner_new_files", tags);
            }
            
        } catch (Exception e) {
            log.error("[S3ScannerTrigger] 执行失败", e);
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("bucket", bucket);
                tags.put("status", "failed");
                metricsClient.incrementCounter("s3_scanner_errors", tags);
            }
        }
    }

    /**
     * 获取视频列表
     */
    private List<String> getVideoList(String bucketName, String prefixValue) throws Exception {
        // 1. 列出目录
        List<String> dirs = s3Client.listDirectories(bucketName, prefixValue);
        log.info("[S3ScannerTrigger] 发现子目录: {} 个", dirs.size());
        if (!dirs.isEmpty()) {
            log.debug("[S3ScannerTrigger] 子目录清单: {}", String.join(", ", dirs));
        }

        List<String> videos = new ArrayList<>();
        
        // 2. 如果没有子目录，直接列举当前前缀下的文件
        if (dirs.isEmpty()) {
            log.info("[S3ScannerTrigger] 无子目录，直接列举当前前缀下文件(非递归)");
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
                
                log.debug("[S3ScannerTrigger] 处理子目录: {}", dirPrefix);
                List<String> files = s3Client.listFiles(bucketName, dirPrefix);
                videos.addAll(filterVideosByExt(files));
            }
        }

        return videos;
    }

    /**
     * 过滤视频文件
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

    /**
     * 从 Redis 获取已处理的文件列表
     */
    private Set<String> getProcessedVideos(String bucketName, String prefixValue) {
        if (redissonClient == null) {
            log.warn("[S3ScannerTrigger] RedissonClient 未配置，无法获取已处理文件列表，将处理所有文件");
            return new HashSet<>();
        }

        try {
            String redisKey = buildRedisKey(bucketName, prefixValue);
            RSet<String> processedSet = redissonClient.getSet(redisKey);
            
            Set<String> processed = new HashSet<>(processedSet.readAll());
            log.debug("[S3ScannerTrigger] 从 Redis 读取已处理文件: key={}, count={}", redisKey, processed.size());
            
            return processed;
        } catch (Exception e) {
            log.error("[S3ScannerTrigger] 从 Redis 获取已处理文件列表失败", e);
            return new HashSet<>();
        }
    }

    /**
     * 识别新增文件
     */
    private List<String> identifyNewVideos(List<String> allVideos, Set<String> processedVideos) {
        return allVideos.stream()
            .filter(video -> !processedVideos.contains(video))
            .collect(Collectors.toList());
    }

    /**
     * 发送新增文件到 MQ
     */
    private int sendToMQ(List<String> newVideos) {
        if (mqProducer == null) {
            log.warn("[S3ScannerTrigger] MQ Producer 未配置，跳过发送到 MQ");
            return 0;
        }

        if (mqTopic == null || mqTopic.isEmpty()) {
            log.warn("[S3ScannerTrigger] MQ Topic 未配置，跳过发送到 MQ");
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
                    log.debug("[S3ScannerTrigger] 发送到 MQ 成功: topic={}, video={}", mqTopic, videoKey);
                } else {
                    failCount++;
                    log.warn("[S3ScannerTrigger] 发送到 MQ 失败: topic={}, video={}", mqTopic, videoKey);
                }
            } catch (Exception e) {
                failCount++;
                log.error("[S3ScannerTrigger] 发送到 MQ 异常: video={}, error={}", videoKey, e.getMessage(), e);
            }
        }

        if (failCount > 0) {
            log.warn("[S3ScannerTrigger] MQ 发送统计: 成功={}, 失败={}", successCount, failCount);
        }

        return successCount;
    }

    /**
     * 构建 MQ 消息体
     */
    private String buildMQMessage(String videoKey) {
        try {
            // 构建消息对象
            Map<String, Object> message = new HashMap<>();
            message.put("videoKey", videoKey);
            message.put("bucket", bucket);
            message.put("timestamp", System.currentTimeMillis());
            
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("[S3ScannerTrigger] 构建 MQ 消息失败: videoKey={}", videoKey, e);
            // 如果 JSON 序列化失败，返回简单的字符串
            return videoKey;
        }
    }

    /**
     * 将文件标记为已处理（写入 Redis）
     */
    private void markAsProcessed(List<String> videos, String bucketName, String prefixValue) {
        if (redissonClient == null) {
            log.warn("[S3ScannerTrigger] RedissonClient 未配置，无法标记已处理文件");
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
            
            log.debug("[S3ScannerTrigger] 已标记 {} 个文件为已处理: key={}", videos.size(), redisKey);
        } catch (Exception e) {
            log.error("[S3ScannerTrigger] 标记已处理文件失败", e);
        }
    }

    /**
     * 构建 Redis 键名
     */
    private String buildRedisKey(String bucketName, String prefixValue) {
        // 使用 bucket 和 prefix 构建唯一的键名
        // 例如: s3_scanner:processed:bucket-name:prefix-path
        String normalizedPrefix = prefixValue.replace("/", ":").replaceAll("[:]+", ":");
        if (normalizedPrefix.endsWith(":")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return REDIS_PROCESSED_KEY_PREFIX + bucketName + ":" + normalizedPrefix;
    }
}

