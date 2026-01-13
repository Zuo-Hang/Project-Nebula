package com.wuxiansheng.shieldarch.orchestrator.config;

import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.stepexecutors.io.S3Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * S3指标客户端适配器
 * 
 * 将MetricsClientAdapter适配为S3Client.MetricsClient接口
 * 这样S3Client就可以使用统一的指标上报功能
 */
@Configuration
public class S3MetricsClientAdapter {
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClientAdapter;
    
    /**
     * 创建S3Client.MetricsClient的实现
     */
    @Bean
    public S3Client.MetricsClient s3MetricsClient() {
        if (metricsClientAdapter == null) {
            return new S3Client.MetricsClient() {
                @Override
                public void timing(String metric, long durationMs, Map<String, String> tags) {}
                @Override
                public void incrementCounter(String metric, Map<String, String> tags) {}
                @Override
                public void recordGauge(String metric, long value, Map<String, String> tags) {}
            };
        }
        
        return new S3Client.MetricsClient() {
            @Override
            public void timing(String metric, long durationMs, Map<String, String> tags) {
                metricsClientAdapter.timing(metric, durationMs, tags);
            }
            
            @Override
            public void incrementCounter(String metric, Map<String, String> tags) {
                metricsClientAdapter.incrementCounter(metric, tags);
            }
            
            @Override
            public void recordGauge(String metric, long value, Map<String, String> tags) {
                metricsClientAdapter.recordGauge(metric, value, tags);
            }
        };
    }
}
