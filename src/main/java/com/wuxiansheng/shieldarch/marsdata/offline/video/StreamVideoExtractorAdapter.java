package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.VideoMeta;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 流式视频提取器适配器
 * 
 * 封装 JavaCVStreamVideoExtractor，提供更友好的 API
 * 支持从 S3 直接处理，不产生本地文件
 */
@Slf4j
public class StreamVideoExtractorAdapter {
    
    private final JavaCVStreamVideoExtractor extractor;
    private final S3Client s3Client;
    
    public StreamVideoExtractorAdapter(S3Client s3Client, int maxWidth, int maxHeight) {
        this.s3Client = s3Client;
        this.extractor = new JavaCVStreamVideoExtractor(maxWidth, maxHeight, 0);
    }
    
    public StreamVideoExtractorAdapter(S3Client s3Client) {
        this(s3Client, 1280, 720);
    }
    
    /**
     * 从 S3 URL 处理视频流并返回所有帧的 Base64
     * 
     * @param s3Url S3 预签名 URL
     * @param options 抽帧选项
     * @return 所有帧的 Base64 编码列表
     */
    public List<String> extractFramesAsBase64(String s3Url, FrameExtractOptions options) 
            throws Exception {
        
        List<String> base64Frames = new ArrayList<>();
        
        extractor.processVideoStream(s3Url, options, (frameIndex, timestamp, image, base64) -> {
            base64Frames.add(base64);
            log.debug("提取帧: index={}, timestamp={}s", frameIndex, timestamp);
        });
        
        return base64Frames;
    }
    
    /**
     * 从 S3 InputStream 处理视频流
     * 
     * @param bucketName S3 桶名称
     * @param objectKey S3 对象键
     * @param format 视频格式
     * @param options 抽帧选项
     * @param processor 帧处理回调
     * @return 视频元数据
     */
    public VideoMeta processFromS3Stream(String bucketName, String objectKey, String format,
                                        FrameExtractOptions options,
                                        JavaCVStreamVideoExtractor.FrameProcessor processor) 
            throws Exception {
        
        // 获取 S3 预签名 URL（推荐方式，FFmpeg 可以直接从 URL 读取）
        String s3Url = s3Client.getFileURL(bucketName, objectKey);
        if (s3Url == null || s3Url.isEmpty()) {
            throw new Exception("无法获取 S3 预签名 URL");
        }
        
        log.info("从 S3 URL 处理视频: bucket={}, key={}, url={}", bucketName, objectKey, s3Url);
        return extractor.processVideoStream(s3Url, options, processor);
    }
    
    /**
     * 从 S3 URL 处理视频流（异步方式）
     * 
     * @param s3Url S3 预签名 URL
     * @param options 抽帧选项
     * @param processor 帧处理回调
     * @return CompletableFuture<VideoMeta>
     */
    public CompletableFuture<VideoMeta> processVideoStreamAsync(String s3Url, 
                                                                 FrameExtractOptions options,
                                                                 JavaCVStreamVideoExtractor.FrameProcessor processor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return extractor.processVideoStream(s3Url, options, processor);
            } catch (Exception e) {
                log.error("异步处理视频流失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 关闭资源
     */
    public void close() {
        extractor.close();
    }
}

