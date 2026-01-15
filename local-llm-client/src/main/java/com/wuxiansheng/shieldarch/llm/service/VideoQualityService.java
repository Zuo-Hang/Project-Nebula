package com.wuxiansheng.shieldarch.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 视频/图片清晰度判断服务
 * 支持视频文件（mp4, avi, mov等）和图片文件（jpg, png, jpeg等）
 */
@Slf4j
@Service
public class VideoQualityService {

    @Value("${local-llm.video.ffprobe-path:ffprobe}")
    private String ffprobePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 视频/图片信息
     */
    @Data
    public static class VideoInfo {
        private int width;
        private int height;
        private String quality;
        private String resolution; // 格式: "1920x1080"

        public VideoInfo(int width, int height, String quality) {
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.resolution = width + "x" + height;
        }
    }

    /**
     * 获取视频/图片清晰度信息
     *
     * @param filePath 文件路径（本地文件路径，如 file:///path/to/file 或 /path/to/file）
     * @return VideoInfo 对象
     * @throws Exception 处理失败时抛出异常
     */
    public VideoInfo getVideoInfo(String filePath) throws Exception {
        // 处理 file:// 协议
        String actualPath = filePath;
        if (filePath.startsWith("file://")) {
            actualPath = filePath.substring(7);
        }

        // 检查文件是否存在
        Path path = Paths.get(actualPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + actualPath);
        }

        // 使用 ffprobe 获取视频/图片信息
        List<String> cmd = new ArrayList<>();
        cmd.add(ffprobePath);
        cmd.add("-v");
        cmd.add("quiet");
        cmd.add("-print_format");
        cmd.add("json");
        cmd.add("-show_streams");
        cmd.add("-select_streams");
        cmd.add("v:0");
        cmd.add(actualPath);

        log.debug("执行 ffprobe 命令: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            throw new RuntimeException("无法启动 ffprobe，请确保已安装 ffmpeg 或设置 local-llm.video.ffprobe-path 配置: " + e.getMessage(), e);
        }

        // 读取输出
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            // 等待进程完成，设置超时30秒
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("ffprobe 执行超时: " + actualPath);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("ffprobe 执行失败，退出码: " + exitCode + ", 错误: " + error.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ffprobe 执行被中断: " + e.getMessage(), e);
        }

        // 解析 JSON 输出
        try {
            JsonNode root = objectMapper.readTree(output.toString());
            JsonNode streams = root.get("streams");

            if (streams == null || !streams.isArray() || streams.size() == 0) {
                throw new RuntimeException("未找到视频流或图片流");
            }

            JsonNode stream = streams.get(0);
            int width = stream.get("width").asInt(0);
            int height = stream.get("height").asInt(0);

            if (width <= 0 || height <= 0) {
                throw new RuntimeException("无法获取有效的分辨率信息: " + width + "x" + height);
            }

            String quality = determineQuality(width, height);

            log.info("获取文件清晰度信息: path={}, resolution={}x{}, quality={}", 
                actualPath, width, height, quality);

            return new VideoInfo(width, height, quality);

        } catch (Exception e) {
            throw new RuntimeException("解析 ffprobe 输出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据分辨率判断清晰度
     * 
     * 行业标准说明：
     * 视频清晰度等级（如1080p、720p）在行业标准中通常指的是视频的"短边"分辨率：
     *   - 横屏视频（宽度 > 高度）：短边 = 高度（垂直分辨率）
     *   - 竖屏视频（高度 > 宽度）：短边 = 宽度（水平分辨率）
     * 
     * 标准清晰度等级（按短边分辨率）：
     *   - 240p: 短边 >= 240 像素（低清）
     *   - 360p: 短边 >= 360 像素（低清）
     *   - 480p: 短边 >= 480 像素（SD - Standard Definition，标清）
     *   - 720p: 短边 >= 720 像素（HD - High Definition，高清）
     *   - 1080p: 短边 >= 1080 像素（Full HD，全高清）
     *   - 1440p: 短边 >= 1440 像素（2K/QHD - Quad HD，2K高清）
     *   - 2160p: 短边 >= 2160 像素（4K/UHD - Ultra HD，超高清）
     *
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @return 清晰度字符串
     */
    private String determineQuality(int width, int height) {
        // 根据视频方向确定判断基准（以短边为准）
        int resolution;
        if (width > height) {
            // 横屏：以高度（短边）判断
            resolution = height;
        } else {
            // 竖屏：以宽度（短边）判断
            resolution = width;
        }

        // 4K (2160p) - Ultra HD / UHD
        if (resolution >= 2160) {
            return "4K (2160p)";
        }

        // 2K (1440p) - Quad HD / QHD
        if (resolution >= 1440) {
            return "2K (1440p)";
        }

        // 1080p - Full HD
        if (resolution >= 1080) {
            return "1080p";
        }

        // 720p - HD (High Definition)
        if (resolution >= 720) {
            return "720p";
        }

        // 480p - SD (Standard Definition)
        if (resolution >= 480) {
            return "480p";
        }

        // 360p - 低清
        if (resolution >= 360) {
            return "360p";
        }

        // 240p - 低清
        if (resolution >= 240) {
            return "240p";
        }

        // 低于240p - 超低清
        return width + "x" + height + " (低清)";
    }

    /**
     * 检查 ffprobe 是否可用
     *
     * @return true 表示可用，false 表示不可用
     */
    public boolean isFfprobeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffprobePath, "-version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("ffprobe 不可用: {}", e.getMessage());
            return false;
        }
    }
}

