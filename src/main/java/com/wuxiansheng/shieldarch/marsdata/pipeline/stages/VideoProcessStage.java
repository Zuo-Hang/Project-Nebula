package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.StoragePort;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 视频处理阶段
 * 对应 Go 版本的 stages.VideoProcessStage
 */
@Slf4j
public class VideoProcessStage implements PipelineStage {

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    private final StoragePort storage;
    private final StoragePort outputStorage;
    private final VideoPort video;
    private final String linkName;

    public VideoProcessStage(StoragePort storage, StoragePort outputStorage, VideoPort video, String linkName) {
        this.storage = storage;
        this.outputStorage = outputStorage != null ? outputStorage : storage;
        this.video = video;
        this.linkName = linkName;
    }

    @Override
    public String name() {
        return "VideoProcess";
    }

    @Override
    public String describe() {
        return "Download, metadata parse, extract frames, save (local + cloud)";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        String videoKey = pipelineCtx.getVideoKey();
        if (videoKey == null || videoKey.isEmpty()) {
            throw new Exception("VideoProcessStage: VideoKey is required in context");
        }

        String linkName = pipelineCtx.getLinkName();
        if (linkName == null || linkName.isEmpty()) {
            linkName = this.linkName;
        }
        if (linkName == null || linkName.isEmpty()) {
            throw new Exception("VideoProcessStage: LinkName is required");
        }
        pipelineCtx.setLinkName(linkName);

        VideoFrameExtractionConfig config = configService != null 
            ? configService.getVideoFrameExtractionConfig(linkName) 
            : null;
        if (config == null) {
            throw new Exception("链路配置未找到: " + linkName);
        }

        // 1. 视频下载
        log.info("1. 下载视频...");
        String localVideoPath = downloadVideo(videoKey);
        log.info("下载完成: {} -> {}", videoKey, localVideoPath);

        // 2. 视频抽帧
        log.info("2. 视频抽帧...");
        FrameExtractOptions options = buildFrameExtractOptions(config);
        List<String> frameImages = video.extractFrames(localVideoPath, options);
        log.info("抽帧完成，共 {} 张图片", frameImages.size());

        // 3. 保存
        // 3.1 本地保存（抽帧时已经保存）
        log.info("3.1 保存到本地...");

        // 3.2 上传云存储
        log.info("3.2 上传到云存储...");
        com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo uploadInfo = 
            uploadToStorage(frameImages, videoKey, linkName, config);

        // 将结果写入 Context
        pipelineCtx.setImagePaths(frameImages);
        pipelineCtx.setLocalVideo(localVideoPath);
        if (uploadInfo != null) {
            pipelineCtx.setS3UploadInfo(uploadInfo);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 下载视频到本地
     */
    private String downloadVideo(String videoKey) throws Exception {
        if (storage == null) {
            throw new Exception("storage 接口未设置");
        }

        // 使用临时目录
        String downloadDir = System.getProperty("java.io.tmpdir") + "/video_downloads";
        File dir = new File(downloadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 构建本地文件路径
        String fileName = new File(videoKey).getName();
        String localPath = downloadDir + "/" + fileName;

        // 下载视频
        storage.download(videoKey, localPath);

        return localPath;
    }

    /**
     * 构建抽帧选项
     */
    private FrameExtractOptions buildFrameExtractOptions(VideoFrameExtractionConfig config) {
        FrameExtractOptions options = new FrameExtractOptions();
        
        // 优先使用链路配置的间隔，如果未设置则使用默认值
        if (config.getFrameInterval() > 0) {
            options.setIntervalSeconds(config.getFrameInterval());
        } else {
            options.setIntervalSeconds(1.0); // 默认1秒
        }

        options.setOutputDir(System.getProperty("java.io.tmpdir") + "/video_extraction");
        options.setStartMillis(0);
        options.setMaxFrames(0); // 0表示无限制
        options.setQuality(2); // 默认质量
        options.setThreads(1); // 默认线程数
        options.setUseKeyFrames(false);

        return options;
    }

    /**
     * 上传到云存储
     */
    private com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo uploadToStorage(
            List<String> frameImages, String videoKey, String linkName, 
            VideoFrameExtractionConfig config) throws Exception {
        
        if (outputStorage == null) {
            log.warn("没有图片需要上传（outputStorage未设置）");
            return createEmptyUploadInfo();
        }

        if (frameImages == null || frameImages.isEmpty()) {
            log.warn("没有图片需要上传");
            return createEmptyUploadInfo();
        }

        // 获取链路配置
        LinkConfig linkCfg = getLinkConfigByName(linkName);
        if (linkCfg == null) {
            throw new Exception("未找到链路配置: " + linkName);
        }

        // 构建 S3 上传路径前缀
        String pathPrefix = extractPathPrefix(linkCfg.getPathList());
        String s3Prefix = buildS3ImagePrefix(linkName, pathPrefix, videoKey, config);

        int uploaded = 0;
        int failed = 0;

        // 并发上传
        for (String imgPath : frameImages) {
            java.io.File imgFile = new java.io.File(imgPath);
            if (!imgFile.exists()) {
                log.warn("   [Upload] 文件不存在，跳过: {}", imgPath);
                failed++;
                continue;
            }

            String objectKey = s3Prefix + "/" + imgFile.getName();
            try {
                outputStorage.upload(imgPath, objectKey);
                uploaded++;
            } catch (Exception e) {
                log.warn("   [Upload] 上传失败 {}: {}", imgFile.getName(), e.getMessage());
                failed++;
            }
        }

        log.info("云存储上传完成，共上传 {} 张图片（失败 {} 张）", uploaded, failed);

        com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo uploadInfo = 
            new com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo();
        uploadInfo.setUploadedCount(uploaded);
        uploadInfo.setFailedCount(failed);
        uploadInfo.setBucketName(linkCfg.getBucketName());
        uploadInfo.setS3URLPrefix(s3Prefix);

        return uploadInfo;
    }

    /**
     * 创建空的上传信息
     */
    private com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo createEmptyUploadInfo() {
        com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo info = 
            new com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo();
        info.setUploadedCount(0);
        info.setFailedCount(0);
        info.setBucketName("");
        info.setS3URLPrefix("");
        return info;
    }

    /**
     * 获取链路配置
     */
    private LinkConfig getLinkConfigByName(String linkName) {
        if (configService == null) {
            return new LinkConfig("sj-ar", 
                Collections.singletonList("OCR_B_" + linkName.toUpperCase()), linkName);
        }

        VideoFrameExtractionConfig config = configService.getVideoFrameExtractionConfig(linkName);
        if (config == null) {
            return new LinkConfig("sj-ar", 
                Collections.singletonList("OCR_B_" + linkName.toUpperCase()), linkName);
        }

        String bucketName = "sj-ar"; // 默认值
        if (config.getInput() != null && config.getInput().getStorage() != null) {
            bucketName = config.getInput().getStorage();
        }

        return new LinkConfig(bucketName, config.getInput().getPathList(), linkName);
    }

    /**
     * 提取路径前缀（只提取根目录名称）
     */
    private String extractPathPrefix(List<String> pathList) {
        if (pathList == null || pathList.isEmpty()) {
            return "";
        }

        String path = pathList.get(0);
        path = path.trim().replaceAll("^/+|/+$", "");
        if (path.isEmpty()) {
            return "";
        }

        String[] parts = path.split("/");
        if (parts.length > 0) {
            return parts[0];
        }

        return "";
    }

    /**
     * 构建S3图片前缀
     */
    private String buildS3ImagePrefix(String linkName, String pathPrefix, String videoKey, 
                                      VideoFrameExtractionConfig config) throws Exception {
        // 从视频路径中提取日期信息
        String[] parts = videoKey.split("/");
        String year = "";
        String month = "";
        String day = "";
        String[] extraSegments = new String[0];

        // 查找年份（4位数字）
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].length() == 4 && parts[i].matches("\\d{4}")) {
                year = parts[i];
                // 查找日期（至少4位数字）
                for (int j = i + 1; j < parts.length - 1; j++) {
                    if (parts[j].matches("\\d{4,}")) {
                        String dateStr = parts[j];
                        if (dateStr.length() >= 4) {
                            month = dateStr.substring(0, 2);
                            day = dateStr.substring(2, 4);
                        }
                        if (j + 1 < parts.length - 1) {
                            extraSegments = Arrays.copyOfRange(parts, j + 1, parts.length - 1);
                        }
                        break;
                    }
                }
                break;
            }
        }

        if (year.isEmpty() || month.isEmpty() || day.isEmpty()) {
            throw new Exception("无法从视频路径解析日期: " + videoKey);
        }

        String imageRoot = pathPrefix;
        if (imageRoot.isEmpty()) {
            imageRoot = "frames";
        }
        if (!imageRoot.endsWith("_Images")) {
            imageRoot = imageRoot + "_Images";
        }

        List<String> segments = new ArrayList<>();
        segments.add(imageRoot);
        segments.add(year);
        segments.add(month + day);
        segments.addAll(Arrays.asList(extraSegments));
        segments.add(generateVideoFolderName(videoKey));

        String prefix = String.join("/", segments.stream()
            .filter(s -> s != null && !s.isEmpty())
            .toArray(String[]::new));

        return prefix.replaceAll("^/+|/+$", "");
    }

    /**
     * 生成视频文件夹名称
     */
    private String generateVideoFolderName(String videoKey) {
        String name = new java.io.File(videoKey).getName();
        name = name.substring(0, name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length());
        name = name.replaceAll("\\s+", "-");
        return name + "-v2";
    }

    /**
     * 链路配置
     */
    @lombok.Data
    private static class LinkConfig {
        private String bucketName;
        private List<String> pathList;
        private String linkName;

        public LinkConfig(String bucketName, List<String> pathList, String linkName) {
            this.bucketName = bucketName;
            this.pathList = pathList;
            this.linkName = linkName;
        }
    }
}

