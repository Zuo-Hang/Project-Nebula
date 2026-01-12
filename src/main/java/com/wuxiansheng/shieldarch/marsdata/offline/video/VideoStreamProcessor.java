package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoMeta;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 视频流处理器（从 S3 流式读取并直接对接 FFmpeg，不产生本地文件）
 * 
 * 使用方法：
 * <pre>
 * VideoStreamProcessor processor = new VideoStreamProcessor(ffmpegPath, ffprobePath, outputDir);
 * 
 * // 从 S3 获取流并处理
 * try (InputStream s3Stream = s3Client.getObjectStream(bucketName, objectKey)) {
 *     List<String> framePaths = processor.extractFramesFromStream(
 *         s3Stream, "video.mp4", options);
 * }
 * </pre>
 */
@Slf4j
public class VideoStreamProcessor {
    
    private final String ffmpegPath;
    private final String ffprobePath;
    private final String outputDir;
    
    public VideoStreamProcessor(String ffmpegPath, String ffprobePath, String outputDir) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.outputDir = outputDir;
    }
    
    /**
     * 从输入流提取视频帧（流式处理，不产生本地视频文件）
     * 
     * @param videoStream S3 视频流
     * @param videoFormat 视频格式（如 "mp4", "avi", "mov"），用于 FFmpeg 的 -f 参数
     * @param videoName 视频名称（用于生成输出文件名）
     * @param options 抽帧选项
     * @return 提取的帧文件路径列表
     * @throws Exception 处理失败时抛出异常
     */
    public List<String> extractFramesFromStream(InputStream videoStream, 
                                                  String videoFormat,
                                                  String videoName,
                                                  FrameExtractOptions options) throws Exception {
        
        // 确保输出目录存在
        String outDir = options.getOutputDir() != null ? options.getOutputDir() : outputDir;
        Files.createDirectories(Paths.get(outDir));
        
        // 构建输出文件名模板
        final String baseName;
        int lastDot = videoName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = videoName.substring(0, lastDot);
        } else {
            baseName = videoName;
        }
        String outputPattern = outDir + "/" + baseName + "_%04d.jpg";
        
        // 构建 FFmpeg 命令
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");  // 覆盖输出文件
        cmd.add("-f");  // 指定输入格式
        cmd.add(videoFormat);  // 如 "mp4", "avi" 等
        cmd.add("-i");  // 输入源
        cmd.add("pipe:0");  // 从标准输入读取（也可以用 "-"）
        
        // 添加视频滤镜
        int frameInterval = calculateFrameInterval(options);
        String selectFilter = String.format("select=not(mod(n\\,%d))", frameInterval);
        cmd.add("-vf");
        cmd.add(selectFilter);
        cmd.add("-vsync");
        cmd.add("vfr");
        
        // 添加线程数
        if (options.getThreads() > 0) {
            cmd.add("-threads");
            cmd.add(String.valueOf(options.getThreads()));
        }
        
        // 添加质量参数
        if (options.getQuality() > 0) {
            cmd.add("-qscale:v");
            cmd.add(String.valueOf(options.getQuality()));
        }
        
        // 添加起始时间
        if (options.getStartMillis() > 0) {
            double startSeconds = options.getStartMillis() / 1000.0;
            cmd.add("-ss");
            cmd.add(String.valueOf(startSeconds));
        }
        
        // 输出文件模式
        cmd.add(outputPattern);
        
        log.info("开始从 S3 流提取视频帧: format={}, videoName={}, outputPattern={}", 
            videoFormat, videoName, outputPattern);
        
        // 创建进程
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);  // 将错误流合并到标准输出
        
        Process process = pb.start();
        
        // 在单独的线程中将 S3 流写入到 FFmpeg 的标准输入
        CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
            try (OutputStream stdin = process.getOutputStream();
                 BufferedInputStream bis = new BufferedInputStream(videoStream)) {
                
                byte[] buffer = new byte[8192];  // 8KB 缓冲区
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    stdin.write(buffer, 0, bytesRead);
                    stdin.flush();
                }
                stdin.close();  // 关闭标准输入，通知 FFmpeg 输入结束
                
            } catch (IOException e) {
                log.error("写入 FFmpeg 标准输入失败", e);
                process.destroyForcibly();  // 发生错误时强制终止进程
            }
        });
        
        // 读取 FFmpeg 的输出（用于日志和错误检测）
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("FFmpeg: {}", line);
            }
        }
        
        // 等待写入完成
        try {
            writeFuture.get(30, TimeUnit.MINUTES);  // 最多等待 30 分钟
        } catch (Exception e) {
            log.error("等待流写入完成失败", e);
            process.destroyForcibly();
            throw new Exception("流写入失败: " + e.getMessage());
        }
        
        // 等待进程完成
        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("FFmpeg 处理超时");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMsg = output.toString();
            if (errorMsg.isEmpty()) {
                errorMsg = "FFmpeg 执行失败，退出码: " + exitCode;
            }
            throw new Exception(errorMsg);
        }
        
        // 收集生成的图片文件
        List<String> imagePaths = new ArrayList<>();
        File outDirFile = new File(outDir);
        if (outDirFile.exists()) {
            File[] files = outDirFile.listFiles((dir, name) -> 
                name.startsWith(baseName + "_") && name.endsWith(".jpg"));
            if (files != null) {
                for (File file : files) {
                    imagePaths.add(file.getAbsolutePath());
                }
                imagePaths.sort(String::compareTo);
            }
        }
        
        log.info("从 S3 流提取视频帧完成，共生成 {} 张图片", imagePaths.size());
        return imagePaths;
    }
    
    /**
     * 从输入流探测视频信息（使用临时文件方案，因为 ffprobe 需要可寻址的输入）
     * 
     * 注意：此方法需要创建临时文件，因为 ffprobe 不支持从 stdin 读取。
     * 如果希望完全避免临时文件，可以使用预签名 URL 的方式。
     * 
     * @param videoStream 视频流
     * @param videoFormat 视频格式
     * @return 视频元信息
     * @throws Exception 探测失败时抛出异常
     */
    public VideoMeta probeFromStream(InputStream videoStream, String videoFormat) throws Exception {
        // 方案1: 使用临时文件（简单但会产生临时文件）
        File tempFile = File.createTempFile("video_probe_", "." + videoFormat);
        tempFile.deleteOnExit();
        
        try {
            // 将流写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedInputStream bis = new BufferedInputStream(videoStream)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // 使用现有的 VideoExtractor 探测
            VideoExtractor extractor = new VideoExtractor(
                ffmpegPath, ffprobePath, outputDir, 1.0, 0, 0, 2, 1);
            return extractor.probe(tempFile.getAbsolutePath());
            
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 从 S3 预签名 URL 探测视频信息（推荐，完全无临时文件）
     * 
     * @param s3Client S3 客户端
     * @param bucketName 桶名称
     * @param objectKey 对象键
     * @return 视频元信息
     * @throws Exception 探测失败时抛出异常
     */
    public VideoMeta probeFromS3URL(S3Client s3Client, String bucketName, String objectKey) throws Exception {
        if (ffprobePath == null || ffprobePath.isEmpty()) {
            throw new Exception("FFprobe 路径未配置");
        }
        
        // 获取预签名 URL
        String videoUrl = s3Client.getFileURL(bucketName, objectKey);
        if (videoUrl == null || videoUrl.isEmpty()) {
            throw new Exception("无法获取 S3 视频 URL");
        }
        
        // 使用 ffprobe 从 URL 读取
        ProcessBuilder pb = new ProcessBuilder(
            ffprobePath,
            "-v", "quiet",
            "-show_entries", "format=duration:stream=r_frame_rate",
            "-of", "csv=p=0",
            videoUrl
        );
        
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.trim().split(",");
                if (parts.length >= 2) {
                    VideoMeta meta = new VideoMeta();
                    meta.setDurationSec(Double.parseDouble(parts[0].trim()));
                    
                    // 解析 FPS
                    String fpsStr = parts[1].trim();
                    if (fpsStr.contains("/")) {
                        String[] fpsParts = fpsStr.split("/");
                        if (fpsParts.length == 2) {
                            double num = Double.parseDouble(fpsParts[0]);
                            double den = Double.parseDouble(fpsParts[1]);
                            if (den > 0) {
                                meta.setFps(num / den);
                            }
                        }
                    } else {
                        meta.setFps(Double.parseDouble(fpsStr));
                    }
                    
                    return meta;
                }
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("FFprobe 执行失败，退出码: " + exitCode);
        }
        
        throw new Exception("无法获取视频信息");
    }
    
    /**
     * 计算帧间隔
     */
    private int calculateFrameInterval(FrameExtractOptions options) {
        // 这里需要先获取视频 FPS，但为了简化，假设使用默认值
        // 实际使用时应该先调用 probe 方法获取 FPS
        double defaultFps = 30.0;
        int frameInterval = (int) (defaultFps * options.getIntervalSeconds());
        return frameInterval < 1 ? 1 : frameInterval;
    }
}

