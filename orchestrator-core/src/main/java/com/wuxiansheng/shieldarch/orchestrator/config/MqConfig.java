package com.wuxiansheng.shieldarch.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * MQ配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mq")
public class MqConfig {
    
    /**
     * Producer配置
     */
    private ProducerConfig producer = new ProducerConfig();
    
    /**
     * Consumer配置
     */
    private Map<String, ConsumerConfig> consumers;
    
    @Data
    public static class ProducerConfig {
        /**
         * 回溯Topic
         */
        private String questBackstraceTopic = "ocr_backstrace";
        
        /**
         * OCR视频捕获Topic
         */
        private String ocrVideoCaptureTopic = "ocr_video_capture";
        
        /**
         * CSD环境 (product/pre/test)
         */
        private String csd = "product";
        
        /**
         * 代理超时时间（毫秒）
         */
        private int proxyTimeout = 5000;
        
        /**
         * 客户端超时时间（毫秒）
         */
        private int clientTimeout = 10000;
        
        /**
         * 客户端重试次数
         */
        private int clientRetry = 3;
        
        /**
         * 连接池大小
         */
        private int poolSize = 20;
    }
    
    @Data
    public static class ConsumerConfig {
        /**
         * 消费者组
         */
        private String group;
        
        /**
         * 并发处理数量（Java版本对应线程数）
         */
        private int goroutineNum = 50;
        
        /**
         * CSD环境
         */
        private String csd = "product";
        
        /**
         * 批量处理数量
         */
        private int batchNum = 1;
    }
}

