package com.wuxiansheng.shieldarch.marsdata.scripts;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.OcrClient;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.mq.Producer;
import com.wuxiansheng.shieldarch.marsdata.offline.video.VideoExtractor;
import com.wuxiansheng.shieldarch.marsdata.pipeline.context.VideoFrameExtractionContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoPort;
import com.wuxiansheng.shieldarch.marsdata.pipeline.runner.PipelineRunner;
import com.wuxiansheng.shieldarch.marsdata.pipeline.stages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.*;

/**
 * B视频处理管道命令行工具
 * 对应 Go 版本的 scripts/b_video_pipeline/main.go
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.wuxiansheng.shieldarch.marsdata")
public class BVideoPipeline implements CommandLineRunner {

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private OcrClient ocrClient;

    @Autowired(required = false)
    private Producer producer;

    private String environment = "dev";
    private String date = "";
    private String linksArg = "saas";
    private List<String> linkNames = new ArrayList<>();
    private boolean skipMQ = false;
    private String subLine = "";
    private String startFrom = "";
    private int concurrentTasks = 1;
    private int maxConcurrentTasks = -1;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BVideoPipeline.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE); // 非Web应用
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        parseArgs(args);

        log.info("启动参数：links={}, env={}, date={}, skipMQ={}, sub_line={}, start_from={}, concurrent={}, max_concurrent={}",
            linksArg, environment, date, skipMQ, subLine, startFrom, concurrentTasks,
            maxConcurrentTasks > 0 ? String.valueOf(maxConcurrentTasks) : "使用配置文件");

        // 设置环境
        if (configService != null) {
            configService.setEnvironment(environment);
        }

        // 加载链路配置
        loadLinkConfigs();

        // 初始化MQ Producer
        if (!skipMQ && producer != null) {
            log.info("初始化 MQ Producer...");
            // TODO: 初始化Producer
        }

        // 解析视频列表
        List<String> videos = resolveVideos();
        if (videos.isEmpty()) {
            log.info("没有视频需要处理，程序正常结束");
            return;
        }

        // 如果指定了起始视频，则从该视频开始过滤
        if (startFrom != null && !startFrom.isEmpty()) {
            videos = filterVideosFromStart(videos, startFrom);
            log.info("从指定视频开始处理，过滤后剩余: {} 个", videos.size());
            if (videos.isEmpty()) {
                log.info("从指定视频开始过滤后没有视频需要处理，程序正常结束");
                return;
            }
        } else {
            log.info("本次待处理视频: {} 个", videos.size());
        }

        // 创建VideoPort适配器
        VideoPort videoAdapter = createVideoAdapter();

        // 处理视频
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < videos.size(); i++) {
            String videoKey = videos.get(i);
            log.info("开始处理视频 {}/{}: {}", i + 1, videos.size(), videoKey);
            try {
                processVideo(videoKey, videoAdapter);
            } catch (Exception e) {
                String msg = String.format("处理视频 %s 失败: %s", videoKey, e.getMessage());
                log.warn(msg, e);
                failed.add(msg);
            }
        }

        if (!failed.isEmpty()) {
            log.warn("部分视频处理失败，共 {} 个，详情:\n{}", failed.size(), String.join("\n", failed));
        } else {
            log.info("所有视频处理完成");
        }
    }

    /**
     * 解析命令行参数
     */
    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-date":
                    if (i + 1 < args.length) {
                        date = args[++i];
                    }
                    break;
                case "-links":
                    if (i + 1 < args.length) {
                        linksArg = args[++i];
                        linkNames = Arrays.asList(linksArg.split(","));
                    }
                    break;
                case "-env":
                    if (i + 1 < args.length) {
                        environment = args[++i];
                    }
                    break;
                case "-skip-mq":
                    skipMQ = true;
                    break;
                case "-sub_line":
                    if (i + 1 < args.length) {
                        subLine = args[++i];
                    }
                    break;
                case "-start-from":
                    if (i + 1 < args.length) {
                        startFrom = args[++i];
                    }
                    break;
                case "-concurrent":
                    if (i + 1 < args.length) {
                        concurrentTasks = Integer.parseInt(args[++i]);
                    }
                    break;
                case "-max-concurrent":
                    if (i + 1 < args.length) {
                        maxConcurrentTasks = Integer.parseInt(args[++i]);
                    }
                    break;
            }
        }

        if (linkNames.isEmpty()) {
            linkNames = Arrays.asList(linksArg.split(","));
        }
    }

    /**
     * 加载链路配置
     */
    private void loadLinkConfigs() throws Exception {
        if (linkNames.isEmpty()) {
            throw new Exception("links 参数不能为空");
        }
        for (String linkName : linkNames) {
            if (configService != null) {
                var config = configService.getVideoFrameExtractionConfig(linkName);
                if (config == null) {
                    throw new Exception("加载链路配置失败 [" + linkName + "]");
                }
                log.info("链路配置已加载: {} (sub_line={})", linkName, subLine);
            }
        }
    }

    /**
     * 解析视频列表
     */
    private List<String> resolveVideos() throws Exception {
        log.info("从 S3 获取视频列表 (日期={}, 链路={}, 当前任务={})...", date, linksArg, concurrentTasks);
        
        // 当前并发任务索引从0开始，所以 concurrentTasks 需要减1
        int currentTaskIndex = concurrentTasks - 1;
        
        PipelineContext listCtx = new VideoFrameExtractionContext();
        MultiLinkListStage listStage = new MultiLinkListStage(
            s3Client, date, linksArg, currentTaskIndex, maxConcurrentTasks);
        
        PipelineRunner runner = new PipelineRunner();
        runner.add(listStage);
        runner.run(listCtx);
        
        List<String> videos = listCtx.getVideoKeys();
        if (videos == null || videos.isEmpty()) {
            log.info("未找到任何视频文件，当前任务无需处理");
            return new ArrayList<>();
        }
        log.info("找到 {} 个视频文件（分配给当前任务）", videos.size());
        return videos;
    }

    /**
     * 从指定视频开始过滤
     */
    private List<String> filterVideosFromStart(List<String> videos, String startVideoKey) {
        if (startVideoKey == null || startVideoKey.isEmpty()) {
            return videos;
        }

        int startIndex = -1;
        for (int i = 0; i < videos.size(); i++) {
            if (videos.get(i).equals(startVideoKey)) {
                startIndex = i;
                break;
            }
        }

        if (startIndex == -1) {
            log.warn("未找到起始视频: {}", startVideoKey);
            return videos;
        }

        return videos.subList(startIndex, videos.size());
    }

    /**
     * 处理视频
     */
    private void processVideo(String videoKey, VideoPort videoAdapter) throws Exception {
        // 确定链路配置
        String linkName = determineLinkName(videoKey);
        if (linkName == null || linkName.isEmpty()) {
            log.warn("无法确定视频 {} 的链路配置，跳过", videoKey);
            return;
        }

        PipelineContext videoCtx = new VideoFrameExtractionContext();
        videoCtx.setVideoKey(videoKey);
        videoCtx.setLinkName(linkName);
        videoCtx.set(com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey.SUBMIT_DATE, date);
        if (subLine != null && !subLine.isEmpty()) {
            // 使用通用的set方法设置sub_line
            videoCtx.set(com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey.SUBMIT_DATE, subLine);
        }

        PipelineRunner pipelineRunner = buildPipelineRunner(linkName, videoAdapter, skipMQ);

        pipelineRunner.run(videoCtx);

        logVideoSummary(videoCtx);
    }

    /**
     * 确定链路名称
     */
    private String determineLinkName(String videoKey) {
        // TODO: 根据videoKey确定链路名称
        if (!linkNames.isEmpty()) {
            return linkNames.get(0);
        }
        return null;
    }

    /**
     * 构建Pipeline运行器
     */
    private PipelineRunner buildPipelineRunner(String linkName, VideoPort videoAdapter, boolean skipMQ) {
        // TODO: 创建StoragePort实现
        // StoragePort inputStorage = s3Client != null ? new S3StoragePort(s3Client, linkName, true) : null;
        // StoragePort outputStorage = s3Client != null ? new S3StoragePort(s3Client, linkName, false) : null;

        PipelineRunner runner = new PipelineRunner();
        
        // TODO: 创建StoragePort实现
        // StoragePort inputStorage = S3StoragePort.newWithLinkNameForInput(s3Client, linkName, configService);
        // StoragePort outputStorage = S3StoragePort.newWithLinkNameForOutput(s3Client, linkName, configService);
        
        // runner.add(new VideoProcessStage(inputStorage, outputStorage, videoAdapter, linkName));
        runner.add(new VideoMetadataStage());
        
        // 创建OCRPort适配器
        com.wuxiansheng.shieldarch.marsdata.pipeline.storage.OcrPortAdapter ocrAdapter = 
            new com.wuxiansheng.shieldarch.marsdata.pipeline.storage.OcrPortAdapter(ocrClient);
        runner.add(new OCRStage(ocrAdapter));
        
        runner.add(new ClassifyStage());
        runner.add(new DedupStage());
        runner.add(new MQStage(skipMQ));
        runner.add(new CleanupStage());

        return runner;
    }

    /**
     * 创建VideoPort适配器
     */
    private VideoPort createVideoAdapter() {
        // TODO: 从配置读取FFmpeg路径
        String ffmpegPath = System.getenv("FFMPEG_PATH");
        if (ffmpegPath == null || ffmpegPath.isEmpty()) {
            ffmpegPath = "ffmpeg";
        }

        String ffprobePath = System.getenv("FFPROBE_PATH");
        if (ffprobePath == null || ffprobePath.isEmpty()) {
            ffprobePath = "ffprobe";
        }

        String outputDir = System.getProperty("java.io.tmpdir") + "/video_extraction";
        VideoExtractor extractor = new VideoExtractor(
            ffmpegPath, ffprobePath, outputDir,
            1.0, 0, 0, 2, 1
        );

        return new VideoPortAdapter(extractor);
    }

    /**
     * 记录视频处理摘要
     */
    private void logVideoSummary(PipelineContext videoCtx) {
        log.info("视频处理摘要: videoKey={}, linkName={}, imageCount={}",
            videoCtx.getVideoKey(), videoCtx.getLinkName(), 
            videoCtx.getImagePaths() != null ? videoCtx.getImagePaths().size() : 0);
    }

    /**
     * VideoPort适配器
     */
    private static class VideoPortAdapter implements VideoPort {
        private final VideoExtractor extractor;

        public VideoPortAdapter(VideoExtractor extractor) {
            this.extractor = extractor;
        }

        @Override
        public com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoMeta probe(String localVideoPath) throws Exception {
            return extractor.probe(localVideoPath);
        }

        @Override
        public List<String> extractFrames(String localVideoPath, 
                                         com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions options) throws Exception {
            return extractor.extractFrames(localVideoPath, options);
        }
    }
}

