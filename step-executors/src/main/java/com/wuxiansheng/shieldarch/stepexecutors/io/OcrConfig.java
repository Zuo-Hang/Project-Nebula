package com.wuxiansheng.shieldarch.stepexecutors.io;

import lombok.Data;

/**
 * OCR服务配置
 */
@Data
public class OcrConfig {
    
    /**
     * OCR服务端点
     */
    private String endpoint;
    
    /**
     * 最小置信度
     */
    private Double confidenceMin;
    
    /**
     * 最小文本长度
     */
    private Integer minTextLen;
    
    /**
     * 每批处理的图片数量
     */
    private Integer batchSize;
    
    /**
     * 最大并发数
     */
    private Integer maxConcurrency;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    
    /**
     * 重试延迟（如 "1s"）
     */
    private String retryDelay;
    
    /**
     * 退避倍数
     */
    private Double backoffMultiplier;
    
    /**
     * HTTP超时时间（如 "120s"）
     */
    private String timeout;
}

