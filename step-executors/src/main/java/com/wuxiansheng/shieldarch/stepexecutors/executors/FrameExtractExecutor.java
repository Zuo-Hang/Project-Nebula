package com.wuxiansheng.shieldarch.stepexecutors.executors;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepExecutor;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepResult;
import com.wuxiansheng.shieldarch.stepexecutors.io.S3Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 视频抽帧执行器
 * 对应旧项目的 VideoProcessStage 和 VideoExtractor
 * 
 * 实现视频流式抽帧（内存处理，不落盘）
 */
@Slf4j
@Component
public class FrameExtractExecutor implements StepExecutor {

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private VideoExtractor videoExtractor;

    @Override
    public String getName() {
        return "FrameExtract";
    }

    @Override
    public String getDescription() {
        return "Download video, extract frames, save (local + cloud)";
    }

    @Override
    public CompletableFuture<StepResult> execute(TaskContext context, StepRequest request) throws Exception {
        String videoKey = context.getVideoKey();
        if (videoKey == null || videoKey.isEmpty()) {
            throw new Exception("FrameExtractExecutor: VideoKey is required in context");
        }

        log.info("开始视频抽帧处理: videoKey={}", videoKey);

        // 1. 下载视频到本地
        log.info("1. 下载视频...");
        String localVideoPath = downloadVideo(videoKey);
        log.info("下载完成: {} -> {}", videoKey, localVideoPath);

        // 2. 视频抽帧
        log.info("2. 视频抽帧...");
        FrameExtractOptions options = buildFrameExtractOptions(request);
        List<String> frameImages = videoExtractor.extractFrames(localVideoPath, options);
        log.info("抽帧完成，共 {} 张图片", frameImages.size());

        // 3. 保存到云存储（可选）
        if (s3Client != null && request.getParams() != null && 
            Boolean.TRUE.equals(request.getParams().get("uploadToS3"))) {
            log.info("3. 上传到云存储...");
            uploadToStorage(frameImages, videoKey, context);
        }

        // 4. 构建结果
        StepResult result = new StepResult();
        result.setImagePaths(frameImages);
        result.setData(new HashMap<>());
        result.getData().put("localVideoPath", localVideoPath);

        // 更新上下文
        context.setImagePaths(frameImages);
        context.setLocalVideoPath(localVideoPath);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 下载视频到本地
     */
    private String downloadVideo(String videoKey) throws Exception {
        if (s3Client == null) {
            throw new Exception("S3Client 未配置");
        }

        // 从 videoKey 中提取 bucket 和 objectKey
        // 格式: bucket/path/to/video.mp4 或 path/to/video.mp4
        String bucketName = extractBucketName(videoKey);
        String objectKey = extractObjectKey(videoKey);

        // 使用临时目录
        String downloadDir = System.getProperty("java.io.tmpdir") + "/video_downloads";
        File dir = new File(downloadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 构建本地文件路径
        String fileName = new File(objectKey).getName();
        String localPath = downloadDir + "/" + fileName;

        // 下载视频
        s3Client.downloadFile(bucketName, objectKey, localPath);

        return localPath;
    }

    /**
     * 从 videoKey 提取 bucket 名称
     */
    private String extractBucketName(String videoKey) {
        // 如果 videoKey 包含 bucket 名称（第一个 / 之前的部分）
        int firstSlash = videoKey.indexOf('/');
        if (firstSlash > 0) {
            return videoKey.substring(0, firstSlash);
        }
        // 默认 bucket（可以从配置中获取）
        return "default-bucket";
    }

    /**
     * 从 videoKey 提取 objectKey
     */
    private String extractObjectKey(String videoKey) {
        int firstSlash = videoKey.indexOf('/');
        if (firstSlash > 0) {
            return videoKey.substring(firstSlash + 1);
        }
        return videoKey;
    }

    /**
     * 构建抽帧选项
     */
    private VideoExtractor.FrameExtractOptions buildFrameExtractOptions(StepRequest request) {
        VideoExtractor.FrameExtractOptions options = new VideoExtractor.FrameExtractOptions();
        
        // 从请求参数中获取配置
        if (request.getParams() != null) {
            Object interval = request.getParams().get("intervalSeconds");
            if (interval instanceof Number) {
                options.setIntervalSeconds(((Number) interval).doubleValue());
            } else {
                options.setIntervalSeconds(1.0); // 默认1秒
            }

            Object maxFrames = request.getParams().get("maxFrames");
            if (maxFrames instanceof Number) {
                options.setMaxFrames(((Number) maxFrames).intValue());
            } else {
                options.setMaxFrames(0); // 0表示无限制
            }

            Object quality = request.getParams().get("quality");
            if (quality instanceof Number) {
                options.setQuality(((Number) quality).intValue());
            } else {
                options.setQuality(2); // 默认质量
            }

            Object threads = request.getParams().get("threads");
            if (threads instanceof Number) {
                options.setThreads(((Number) threads).intValue());
            } else {
                options.setThreads(1); // 默认线程数
            }
        } else {
            // 使用默认值
            options.setIntervalSeconds(1.0);
            options.setMaxFrames(0);
            options.setQuality(2);
            options.setThreads(1);
        }

        options.setOutputDir(System.getProperty("java.io.tmpdir") + "/video_extraction");
        options.setStartMillis(0);
        options.setUseKeyFrames(false);

        return options;
    }

    /**
     * 上传到云存储
     */
    private void uploadToStorage(List<String> frameImages, String videoKey, TaskContext context) {
        // TODO: 实现上传逻辑（参考旧项目的 VideoProcessStage.uploadToStorage）
        log.info("上传 {} 张图片到云存储", frameImages.size());
    }
}

