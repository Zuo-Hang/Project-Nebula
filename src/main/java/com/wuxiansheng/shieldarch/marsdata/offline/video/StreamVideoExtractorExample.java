package com.wuxiansheng.shieldarch.marsdata.offline.video;

import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.FrameExtractOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 流式视频提取器使用示例
 * 
 * 展示如何使用 JavaCVStreamVideoExtractor 实现内存流式抽帧
 */
@Slf4j
public class StreamVideoExtractorExample {
    
    /**
     * 示例1: 从 S3 URL 处理视频，直接转换为 Base64 发送给 LLM
     */
    public static void example1_ProcessFromS3Url(S3Client s3Client, String bucketName, 
                                                  String objectKey) throws Exception {
        
        // 1. 获取 S3 预签名 URL
        String s3Url = s3Client.getFileURL(bucketName, objectKey);
        log.info("S3 URL: {}", s3Url);
        
        // 2. 创建提取器（1280x720，同步处理）
        JavaCVStreamVideoExtractor extractor = new JavaCVStreamVideoExtractor(1280, 720, 0);
        
        // 3. 配置抽帧选项（1fps，即每秒1帧）
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(1.0);  // 每秒1帧
        options.setMaxFrames(100);  // 最多100帧
        options.setStartMillis(0);
        
        // 4. 处理视频流
        extractor.processVideoStream(s3Url, options, (frameIndex, timestamp, image, base64) -> {
            // 5. 直接在内存中处理每一帧
            log.info("处理帧: index={}, timestamp={}s, base64长度={}", 
                frameIndex, timestamp, base64.length());
            
            // 6. 异步推入编排器（发送给 LLM）
            // orchestrator.dispatch(base64);
            
            // 或者保存到列表
            // frameList.add(base64);
        });
        
        extractor.close();
    }
    
    /**
     * 示例2: 使用适配器简化调用
     */
    public static void example2_UseAdapter(S3Client s3Client, String bucketName, 
                                           String objectKey) throws Exception {
        
        // 1. 创建适配器
        StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(s3Client);
        
        // 2. 配置选项
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(0.5);  // 每0.5秒1帧（2fps）
        options.setMaxFrames(50);
        
        // 3. 处理视频流
        adapter.processFromS3Stream(bucketName, objectKey, "mp4", options, 
            (frameIndex, timestamp, image, base64) -> {
                log.info("提取帧: index={}, timestamp={}s", frameIndex, timestamp);
                // 发送给 LLM 或其他处理
            });
        
        adapter.close();
    }
    
    /**
     * 示例3: 异步处理多个视频
     */
    public static void example3_AsyncProcessing(S3Client s3Client, 
                                                List<String> videoKeys) throws Exception {
        
        StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(s3Client);
        
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(1.0);
        options.setMaxFrames(100);
        
        // 异步处理所有视频
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        for (String videoKey : videoKeys) {
            CompletableFuture<Void> future = adapter.processVideoStreamAsync(
                s3Client.getFileURL("bucket", videoKey), 
                options,
                (frameIndex, timestamp, image, base64) -> {
                    // 处理每一帧
                    log.debug("处理视频 {} 的帧 {}", videoKey, frameIndex);
                }
            ).thenRun(() -> {
                log.info("视频处理完成: {}", videoKey);
            });
            
            futures.add(future);
        }
        
        // 等待所有视频处理完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        adapter.close();
    }
    
    /**
     * 示例4: 提取所有帧为 Base64 列表
     */
    public static List<String> example4_ExtractAllFrames(S3Client s3Client, 
                                                          String bucketName, 
                                                          String objectKey) throws Exception {
        
        StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(s3Client);
        
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(1.0);
        options.setMaxFrames(0);  // 无限制
        
        // 提取所有帧
        String s3Url = s3Client.getFileURL(bucketName, objectKey);
        List<String> base64Frames = adapter.extractFramesAsBase64(s3Url, options);
        
        log.info("提取完成，共 {} 帧", base64Frames.size());
        
        adapter.close();
        return base64Frames;
    }
    
    /**
     * 示例5: 定价业务场景（低帧率采样）
     */
    public static void example5_PricingBusiness(S3Client s3Client, String bucketName, 
                                                String objectKey) throws Exception {
        
        // 定价业务通常只需要 1fps 甚至更低（如 0.5fps）
        StreamVideoExtractorAdapter adapter = new StreamVideoExtractorAdapter(
            s3Client, 1280, 720);  // 降低分辨率减少内存
        
        FrameExtractOptions options = new FrameExtractOptions();
        options.setIntervalSeconds(2.0);  // 每2秒1帧（0.5fps）
        options.setMaxFrames(30);  // 最多30帧（1分钟视频）
        options.setStartMillis(0);
        
        adapter.processFromS3Stream(bucketName, objectKey, "mp4", options,
            (frameIndex, timestamp, image, base64) -> {
                // 直接发送给 LLM 进行价格识别
                log.info("价格识别帧: timestamp={}s, base64长度={}", timestamp, base64.length());
                
                // LLM 调用示例
                // llmClient.recognizePrice(base64);
            });
        
        adapter.close();
    }
}

