package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoMeta;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频抽帧器（基于 FFmpeg 实现）
 * 对应 Go 版本的 video.VideoExtractor
 */
@Slf4j
public class VideoExtractor {

    private final String ffmpegPath;
    private final String ffprobePath;
    private final String outputDir;
    private final double timeInterval;
    private final long startMillis;
    private final int maxFrames;
    private final int quality;
    private final int threads;

    public VideoExtractor(String ffmpegPath, String ffprobePath, String outputDir,
                         double timeInterval, long startMillis, int maxFrames,
                         int quality, int threads) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.outputDir = outputDir;
        this.timeInterval = timeInterval;
        this.startMillis = startMillis;
        this.maxFrames = maxFrames;
        this.quality = quality;
        this.threads = threads;
    }

    /**
     * 探测视频信息
     */
    public VideoMeta probe(String localVideoPath) throws Exception {
        double fps = getFPS(localVideoPath);
        double duration = getDuration(localVideoPath);

        VideoMeta meta = new VideoMeta();
        meta.setFps(fps);
        meta.setDurationSec(duration);
        meta.setWidth(0); // 暂时不获取
        meta.setHeight(0); // 暂时不获取
        return meta;
    }

    /**
     * 提取视频帧
     */
    public List<String> extractFrames(String localVideoPath, FrameExtractOptions options) throws Exception {
        VideoMeta videoInfo = probe(localVideoPath);
        log.info("视频基础信息 | FPS={:.2f} | 时长={:.2f}秒 | 理论总帧数={:.0f}",
            videoInfo.getFps(), videoInfo.getDurationSec(), 
            videoInfo.getFps() * videoInfo.getDurationSec());

        // 计算抽帧步长
        int frameInterval = (int) (videoInfo.getFps() * options.getIntervalSeconds());
        if (frameInterval < 1) {
            frameInterval = 1;
        }

        // 确保输出目录存在
        String outDir = options.getOutputDir() != null ? options.getOutputDir() : outputDir;
        Files.createDirectories(Paths.get(outDir));

        // 执行抽帧
        return extractFramesByInterval(localVideoPath, outDir, videoInfo, frameInterval, options);
    }

    /**
     * 按间隔抽帧
     */
    private List<String> extractFramesByInterval(String videoPath, String outDir,
                                                 VideoMeta videoInfo, int frameInterval,
                                                 FrameExtractOptions options) throws Exception {
        // 计算起始时间（毫秒转秒）
        double startSeconds = (options.getStartMillis() > 0 
            ? options.getStartMillis() : startMillis) / 1000.0;

        // 构建select滤镜：每隔frameInterval帧选择一帧
        String selectFilter = String.format("select=not(mod(n\\,%d))", frameInterval);

        int theoreticalFrames = (int) (videoInfo.getFps() * videoInfo.getDurationSec());
        int estTotal = theoreticalFrames / frameInterval;
        int maxFrames = options.getMaxFrames() > 0 ? options.getMaxFrames() : this.maxFrames;
        if (maxFrames > 0 && estTotal > maxFrames) {
            estTotal = maxFrames;
        }

        log.info("视频信息: FPS={:.2f}, 时长={:.2f}秒, 理论总帧数={}帧，预估抽帧数量: {} 张",
            videoInfo.getFps(), videoInfo.getDurationSec(), theoreticalFrames, estTotal);

        // 构建输出文件名模板
        String videoName = new File(videoPath).getName();
        String baseName = videoName.substring(0, videoName.lastIndexOf('.') > 0 
            ? videoName.lastIndexOf('.') : videoName.length());
        String outputPattern = outDir + "/" + baseName + "_%04d.jpg";

        // 构建FFmpeg命令
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(videoPath);
        cmd.add("-vf");
        cmd.add(selectFilter);
        cmd.add("-vsync");
        cmd.add("vfr");

        if (options.getThreads() > 0 || threads > 0) {
            cmd.add("-threads");
            cmd.add(String.valueOf(options.getThreads() > 0 ? options.getThreads() : threads));
        }

        int quality = options.getQuality() > 0 ? options.getQuality() : this.quality;
        cmd.add("-qscale:v");
        cmd.add(String.valueOf(quality));

        if (startSeconds > 0) {
            cmd.add("-ss");
            cmd.add(String.valueOf(startSeconds));
        }

        cmd.add(outputPattern);

        // 执行FFmpeg命令
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 读取输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("FFmpeg执行失败，退出码: " + exitCode);
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

        log.info("抽帧完成，共生成 {} 张图片", imagePaths.size());
        return imagePaths;
    }

    /**
     * 获取视频FPS
     */
    private double getFPS(String videoPath) throws Exception {
        if (ffprobePath == null || ffprobePath.isEmpty()) {
            return 30.0; // 默认FPS
        }

        // 方案1: 尝试使用nb_frames和duration计算真实FPS
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet",
                "-select_streams", "v:0",
                "-show_entries", "stream=nb_frames:format=duration",
                "-of", "csv=p=0",
                videoPath
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\n");
                    if (parts.length >= 2) {
                        double nbFrames = Double.parseDouble(parts[0].trim());
                        double duration = Double.parseDouble(parts[1].trim());
                        if (duration > 0 && nbFrames > 0) {
                            double calculatedFPS = nbFrames / duration;
                            if (calculatedFPS > 1 && calculatedFPS < 120) {
                                return calculatedFPS;
                            }
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("方案1获取FPS失败: {}", e.getMessage());
        }

        // 方案2: 使用r_frame_rate
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate",
                "-of", "csv=p=0",
                videoPath
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String fpsStr = reader.readLine();
                if (fpsStr != null) {
                    fpsStr = fpsStr.trim();
                    // 解析分数形式的FPS（如 30/1）
                    if (fpsStr.contains("/")) {
                        String[] parts = fpsStr.split("/");
                        if (parts.length == 2) {
                            double num = Double.parseDouble(parts[0]);
                            double den = Double.parseDouble(parts[1]);
                            if (den > 0 && num > 0 && num < 1000 && (num/den) > 0 && (num/den) < 120) {
                                return num / den;
                            }
                        }
                    } else {
                        double num = Double.parseDouble(fpsStr);
                        if (num > 0 && num < 120) {
                            return num;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("方案2获取FPS失败: {}", e.getMessage());
        }

        return 30.0; // 默认FPS
    }

    /**
     * 获取视频时长
     */
    private double getDuration(String videoPath) throws Exception {
        if (ffprobePath == null || ffprobePath.isEmpty()) {
            throw new Exception("FFprobe路径未配置");
        }

        ProcessBuilder pb = new ProcessBuilder(
            ffprobePath,
            "-v", "quiet",
            "-show_entries", "format=duration",
            "-of", "csv=p=0",
            videoPath
        );

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String durationStr = reader.readLine();
            if (durationStr != null) {
                durationStr = durationStr.trim();
                if (durationStr.equals("N/A") || durationStr.isEmpty()) {
                    throw new Exception("无法获取视频时长，可能不是有效的视频文件");
                }
                return Double.parseDouble(durationStr);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("FFprobe执行失败，退出码: " + exitCode);
        }

        throw new Exception("无法获取视频时长");
    }
}

