package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoMeta;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于 JavaCV 的内存流式视频抽帧器
 * 
 * 核心特性：
 * 1. 直接从网络流（S3 URL/InputStream）读取，不落盘
 * 2. 内存中处理，支持跳帧采样（如 1fps）
 * 3. 可直接转换为 Base64，准备发送给 LLM
 * 4. 支持异步处理，避免阻塞
 * 
 * 使用示例：
 * <pre>
 * JavaCVStreamVideoExtractor extractor = new JavaCVStreamVideoExtractor();
 * 
 * // 方式1: 从 S3 URL 处理
 * extractor.processVideoStream(s3Url, options, (frameIndex, image, base64) -> {
 *     // 异步推入编排器
 *     orchestrator.dispatch(base64);
 * });
 * 
 * // 方式2: 从 InputStream 处理
 * try (InputStream is = s3Client.getObjectStream(bucket, key)) {
 *     extractor.processVideoStream(is, "mp4", options, processor);
 * }
 * </pre>
 */
@Slf4j
public class JavaCVStreamVideoExtractor {
    
    private final ExecutorService executorService;
    private final int maxWidth;
    private final int maxHeight;
    
    /**
     * 帧处理回调接口
     */
    @FunctionalInterface
    public interface FrameProcessor {
        /**
         * 处理每一帧
         * 
         * @param frameIndex 帧索引（从0开始）
         * @param timestamp 时间戳（秒）
         * @param image BufferedImage 对象
         * @param base64 Base64 编码的图片（JPEG格式）
         */
        void process(int frameIndex, double timestamp, BufferedImage image, String base64);
    }
    
    /**
     * 构造函数
     * 
     * @param maxWidth 最大宽度（用于缩放，减少内存占用，0表示不缩放）
     * @param maxHeight 最大高度（用于缩放，减少内存占用，0表示不缩放）
     * @param asyncThreads 异步处理线程数（0表示同步处理）
     */
    public JavaCVStreamVideoExtractor(int maxWidth, int maxHeight, int asyncThreads) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.executorService = asyncThreads > 0 
            ? Executors.newFixedThreadPool(asyncThreads) 
            : null;
    }
    
    /**
     * 默认构造函数（1280x720，同步处理）
     */
    public JavaCVStreamVideoExtractor() {
        this(1280, 720, 0);
    }
    
    /**
     * 从 S3 URL 处理视频流（核心方法，推荐方式）
     * 
     * FFmpegFrameGrabber 可以直接从 URL 读取，这是真正的"不落盘"方案
     * 
     * @param s3Url S3 预签名 URL 或公开 URL
     * @param options 抽帧选项
     * @param processor 帧处理回调
     * @return 视频元数据
     * @throws Exception 处理失败时抛出异常
     */
    public VideoMeta processVideoStream(String s3Url, FrameExtractOptions options, 
                                        FrameProcessor processor) throws Exception {
        log.info("开始处理视频流: url={}", s3Url);
        
        FFmpegFrameGrabber grabber = null;
        Java2DFrameConverter converter = null;
        
        try {
            // 1. 创建 FFmpegFrameGrabber（直接从 URL 读取，不落盘）
            grabber = new FFmpegFrameGrabber(s3Url);
            
            // 2. 优化网络流读取设置
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("fflags", "nobuffer");
            grabber.setOption("flags", "low_delay");
            grabber.setOption("probesize", "32");  // 减少探测大小，加快启动
            grabber.setOption("analyzeduration", "1000000");  // 减少分析时间
            
            // 3. 设置分辨率（减少内存占用）
            if (maxWidth > 0 && maxHeight > 0) {
                grabber.setImageWidth(maxWidth);
                grabber.setImageHeight(maxHeight);
            }
            
            // 4. 启动抓取器
            grabber.start();
            
            // 5. 获取视频元数据
            VideoMeta meta = extractVideoMeta(grabber);
            log.info("视频元数据: FPS={}, 时长={}秒, 尺寸={}x{}", 
                meta.getFps(), meta.getDurationSec(), meta.getWidth(), meta.getHeight());
            
            // 6. 计算跳帧策略
            int frameInterval = calculateFrameInterval(meta, options);
            log.info("抽帧策略: 间隔={}帧 (约{}fps)", frameInterval, meta.getFps() / frameInterval);
            
            // 7. 初始化转换器
            converter = new Java2DFrameConverter();
            
            // 8. 处理视频帧
            return processFrames(grabber, converter, meta, frameInterval, options, processor);
            
        } catch (Exception e) {
            log.error("流式处理视频异常", e);
            throw e;
        } finally {
            // 清理资源
            if (converter != null) {
                try {
                    converter.close();
                } catch (Exception e) {
                    log.warn("关闭转换器失败", e);
                }
            }
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.close();
                } catch (Exception e) {
                    log.warn("关闭抓取器失败", e);
                }
            }
        }
    }
    
    /**
     * 从 InputStream 处理视频流（核心方法）
     * 
     * 注意：FFmpegFrameGrabber 需要文件路径或 URL，不能直接从 InputStream 读取
     * 推荐使用 processVideoStream(String s3Url, ...) 方法，从 S3 预签名 URL 读取
     * 
     * 如果必须从 InputStream 处理，需要先将流写入临时文件（会产生临时文件）
     * 
     * @param videoStream 视频输入流
     * @param format 视频格式（如 "mp4", "avi", "mov"）
     * @param options 抽帧选项
     * @param processor 帧处理回调
     * @return 视频元数据
     * @throws Exception 处理失败时抛出异常
     */
    public VideoMeta processVideoStream(InputStream videoStream, String format,
                                        FrameExtractOptions options,
                                        FrameProcessor processor) throws Exception {
        
        // 由于 FFmpegFrameGrabber 不支持直接从 InputStream 读取，
        // 这里提供一个折中方案：使用临时文件
        // 注意：这会产生临时文件，不是真正的"不落盘"
        // 推荐使用 processVideoStream(String s3Url, ...) 方法
        
        java.io.File tempFile = null;
        try {
            // 创建临时文件
            tempFile = java.io.File.createTempFile("video_stream_", "." + format);
            tempFile.deleteOnExit();
            
            // 将流写入临时文件
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                 java.io.BufferedInputStream bis = new java.io.BufferedInputStream(videoStream)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // 从临时文件处理
            return processVideoStreamFromFile(tempFile.getAbsolutePath(), options, processor);
            
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 从本地文件路径处理视频流
     */
    private VideoMeta processVideoStreamFromFile(String filePath, FrameExtractOptions options,
                                                 FrameProcessor processor) throws Exception {
        
        FFmpegFrameGrabber grabber = null;
        Java2DFrameConverter converter = null;
        
        try {
            // 1. 创建 FFmpegFrameGrabber
            grabber = new FFmpegFrameGrabber(filePath);
            
            // 2. 优化设置
            grabber.setOption("fflags", "nobuffer");
            grabber.setOption("flags", "low_delay");
            
            // 3. 设置分辨率（减少内存占用）
            if (maxWidth > 0 && maxHeight > 0) {
                grabber.setImageWidth(maxWidth);
                grabber.setImageHeight(maxHeight);
            }
            
            // 4. 启动抓取器
            grabber.start();
            
            // 5. 获取视频元数据
            VideoMeta meta = extractVideoMeta(grabber);
            log.info("视频元数据: FPS={}, 时长={}秒, 尺寸={}x{}", 
                meta.getFps(), meta.getDurationSec(), meta.getWidth(), meta.getHeight());
            
            // 6. 计算跳帧策略
            int frameInterval = calculateFrameInterval(meta, options);
            log.info("抽帧策略: 间隔={}帧 (约{}fps)", frameInterval, meta.getFps() / frameInterval);
            
            // 7. 初始化转换器
            converter = new Java2DFrameConverter();
            
            // 8. 开始抽帧处理
            return processFrames(grabber, converter, meta, frameInterval, options, processor);
            
        } catch (Exception e) {
            log.error("流式处理视频异常", e);
            throw e;
        } finally {
            // 清理资源
            if (converter != null) {
                try {
                    converter.close();
                } catch (Exception e) {
                    log.warn("关闭转换器失败", e);
                }
            }
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.close();
                } catch (Exception e) {
                    log.warn("关闭抓取器失败", e);
                }
            }
        }
    }
    
    /**
     * 处理视频帧（核心逻辑）
     */
    private VideoMeta processFrames(FFmpegFrameGrabber grabber, Java2DFrameConverter converter,
                                   VideoMeta meta, int frameInterval, FrameExtractOptions options,
                                   FrameProcessor processor) throws Exception {
        
        int frameCount = 0;
        int extractedCount = 0;
        Frame frame;
        double startTime = options.getStartMillis() > 0 
            ? options.getStartMillis() / 1000.0 : 0.0;
        
        // 如果设置了起始时间，先跳转到该位置
        if (startTime > 0) {
            long startFrame = (long) (startTime * meta.getFps());
            grabber.setFrameNumber((int) startFrame);
        }
        
        int maxFrames = options.getMaxFrames() > 0 ? options.getMaxFrames() : Integer.MAX_VALUE;
        
        while ((frame = grabber.grabImage()) != null && extractedCount < maxFrames) {
            // 跳帧采样：每隔 frameInterval 帧处理一帧
            if (frameCount % frameInterval == 0) {
                double timestamp = grabber.getTimestamp() / 1_000_000.0; // 微秒转秒
                
                // 转换为 BufferedImage
                BufferedImage image = converter.convert(frame);
                if (image == null) {
                    log.warn("帧转换失败，跳过: frameIndex={}", frameCount);
                    frameCount++;
                    continue;
                }
                
                // 转换为 Base64
                String base64 = imageToBase64(image);
                
                // 处理帧（同步或异步）
                if (executorService != null) {
                    // 异步处理
                    final int finalFrameIndex = extractedCount;
                    final double finalTimestamp = timestamp;
                    final BufferedImage finalImage = image;
                    final String finalBase64 = base64;
                    
                    executorService.submit(() -> {
                        try {
                            processor.process(finalFrameIndex, finalTimestamp, finalImage, finalBase64);
                        } catch (Exception e) {
                            log.error("处理帧失败: frameIndex={}", finalFrameIndex, e);
                        }
                    });
                } else {
                    // 同步处理
                    processor.process(extractedCount, timestamp, image, base64);
                }
                
                extractedCount++;
            }
            
            frameCount++;
        }
        
        log.info("流式处理完成: 总帧数={}, 抽取帧数={}", frameCount, extractedCount);
        
        return meta;
    }
    
    /**
     * 提取视频元数据
     */
    private VideoMeta extractVideoMeta(FFmpegFrameGrabber grabber) throws Exception {
        VideoMeta meta = new VideoMeta();
        meta.setFps(grabber.getFrameRate());
        meta.setDurationSec(grabber.getLengthInTime() / 1_000_000.0); // 微秒转秒
        meta.setWidth(grabber.getImageWidth());
        meta.setHeight(grabber.getImageHeight());
        return meta;
    }
    
    /**
     * 计算帧间隔（跳帧策略）
     */
    private int calculateFrameInterval(VideoMeta meta, FrameExtractOptions options) {
        // 根据 intervalSeconds 计算需要跳过的帧数
        // 例如：FPS=30, intervalSeconds=1.0 -> 每30帧取1帧（1fps）
        int interval = (int) (meta.getFps() * options.getIntervalSeconds());
        return interval < 1 ? 1 : interval;
    }
    
    /**
     * 将 BufferedImage 转换为 Base64 编码的 JPEG
     */
    public static String imageToBase64(BufferedImage image) throws IOException {
        return imageToBase64(image, "jpg", 0.85f);
    }
    
    /**
     * 将 BufferedImage 转换为 Base64 编码
     * 
     * @param image 图片对象
     * @param format 图片格式（jpg, png）
     * @param quality 压缩质量（0.0-1.0，仅对 JPEG 有效）
     */
    public static String imageToBase64(BufferedImage image, String format, float quality) 
            throws IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            // JPEG 格式，支持质量压缩
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            javax.imageio.stream.ImageOutputStream ios = 
                javax.imageio.ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            writer.dispose();
            ios.close();
        } else {
            // 其他格式
            ImageIO.write(image, format, baos);
        }
        
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    
    /**
     * 关闭资源
     */
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

