package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

/**
 * 使用示例：如何从 S3 获取流并直接对接 FFmpeg，不产生本地文件
 * 
 * 这个示例展示了两种方式：
 * 1. 从 S3 流直接处理（推荐，完全不产生临时文件）
 * 2. 从 S3 URL 探测视频信息（推荐，完全不产生临时文件）
 */
@Slf4j
public class VideoStreamProcessorExample {
    
    /**
     * 示例1: 从 S3 流提取视频帧（完全不产生本地视频文件）
     * 
     * @param s3Client S3 客户端
     * @param bucketName S3 桶名称
     * @param objectKey S3 对象键（视频文件路径）
     * @param ffmpegPath FFmpeg 可执行文件路径
     * @param ffprobePath FFprobe 可执行文件路径
     * @param outputDir 输出目录（用于保存提取的帧图片）
     */
    public static void extractFramesFromS3Stream(S3Client s3Client,
                                                  String bucketName,
                                                  String objectKey,
                                                  String ffmpegPath,
                                                  String ffprobePath,
                                                  String outputDir) {
        
        // 创建流处理器
        VideoStreamProcessor processor = new VideoStreamProcessor(
            ffmpegPath, ffprobePath, outputDir);
        
        // 从对象键推断视频格式（通常可以从文件扩展名判断）
        String videoFormat = getVideoFormatFromKey(objectKey);  // 如 "mp4", "avi", "mov"
        
        // 配置抽帧选项
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(1.0);  // 每秒抽取一帧
        options.setMaxFrames(100);  // 最多抽取 100 帧
        options.setQuality(2);  // 图片质量（2-31，数字越小质量越高）
        options.setThreads(4);  // FFmpeg 线程数
        options.setOutputDir(outputDir);
        
        // 从 S3 获取流并处理（不产生本地视频文件）
        try (InputStream s3Stream = s3Client.getObjectStream(bucketName, objectKey)) {
            
            // 提取视频帧（流式处理，直接传递给 FFmpeg）
            List<String> framePaths = processor.extractFramesFromStream(
                s3Stream, 
                videoFormat, 
                objectKey,  // 视频名称，用于生成输出文件名
                options
            );
            
            log.info("成功从 S3 流提取 {} 帧，保存路径: {}", 
                framePaths.size(), framePaths.get(0));
            
        } catch (Exception e) {
            log.error("从 S3 流提取视频帧失败", e);
        }
    }
    
    /**
     * 示例2: 从 S3 URL 探测视频信息（完全不产生临时文件，推荐方式）
     * 
     * @param s3Client S3 客户端
     * @param bucketName S3 桶名称
     * @param objectKey S3 对象键
     * @param ffprobePath FFprobe 可执行文件路径
     * @param outputDir 输出目录
     */
    public static void probeVideoFromS3URL(S3Client s3Client,
                                           String bucketName,
                                           String objectKey,
                                           String ffprobePath,
                                           String outputDir) {
        
        // 创建流处理器
        VideoStreamProcessor processor = new VideoStreamProcessor(
            null, ffprobePath, outputDir);
        
        try {
            // 从 S3 URL 探测视频信息（使用预签名 URL，不产生临时文件）
            var videoMeta = processor.probeFromS3URL(s3Client, bucketName, objectKey);
            
            log.info("视频信息: FPS={}, 时长={}秒, 宽度={}, 高度={}", 
                videoMeta.getFps(), 
                videoMeta.getDurationSec(),
                videoMeta.getWidth(),
                videoMeta.getHeight());
            
        } catch (Exception e) {
            log.error("从 S3 URL 探测视频信息失败", e);
        }
    }
    
    /**
     * 示例3: 完整的处理流程（探测 + 提取帧）
     */
    public static void processVideoFromS3(S3Client s3Client,
                                          String bucketName,
                                          String objectKey,
                                          String ffmpegPath,
                                          String ffprobePath,
                                          String outputDir) {
        
        VideoStreamProcessor processor = new VideoStreamProcessor(
            ffmpegPath, ffprobePath, outputDir);
        
        String videoFormat = getVideoFormatFromKey(objectKey);
        
        try {
            // 步骤1: 从 S3 URL 探测视频信息（不产生临时文件）
            log.info("开始探测视频信息: {}", objectKey);
            var videoMeta = processor.probeFromS3URL(s3Client, bucketName, objectKey);
            log.info("视频信息: FPS={}, 时长={}秒", 
                String.format("%.2f", videoMeta.getFps()), 
                String.format("%.2f", videoMeta.getDurationSec()));
            
            // 步骤2: 根据视频信息配置抽帧选项
            FrameExtractOptions options = new FrameExtractOptions();
            options.setIntervalSeconds(1.0);  // 每秒一帧
            options.setMaxFrames((int) (videoMeta.getDurationSec() * 0.1));  // 最多抽取总帧数的10%
            options.setQuality(2);
            options.setThreads(4);
            options.setOutputDir(outputDir);
            
            // 步骤3: 从 S3 流提取帧（不产生本地视频文件）
            log.info("开始从 S3 流提取视频帧");
            try (InputStream s3Stream = s3Client.getObjectStream(bucketName, objectKey)) {
                List<String> framePaths = processor.extractFramesFromStream(
                    s3Stream, videoFormat, objectKey, options);
                log.info("提取完成，共生成 {} 张图片", framePaths.size());
            }
            
        } catch (Exception e) {
            log.error("处理 S3 视频失败", e);
        }
    }
    
    /**
     * 从对象键推断视频格式
     */
    private static String getVideoFormatFromKey(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return "mp4";  // 默认格式
        }
        
        int lastDot = objectKey.lastIndexOf('.');
        if (lastDot > 0 && lastDot < objectKey.length() - 1) {
            String ext = objectKey.substring(lastDot + 1).toLowerCase();
            // 将常见扩展名映射到 FFmpeg 格式
            switch (ext) {
                case "mp4":
                case "m4v":
                    return "mp4";
                case "avi":
                    return "avi";
                case "mov":
                    return "mov";
                case "mkv":
                    return "matroska";
                case "flv":
                    return "flv";
                case "webm":
                    return "webm";
                default:
                    return "mp4";  // 默认格式
            }
        }
        
        return "mp4";  // 默认格式
    }
}

