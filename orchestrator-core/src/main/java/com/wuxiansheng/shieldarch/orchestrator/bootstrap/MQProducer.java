package com.wuxiansheng.shieldarch.orchestrator.bootstrap;

import com.wuxiansheng.shieldarch.orchestrator.config.MqConfig;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息队列生产者
 * 对应旧项目的 mq.Producer
 */
@Slf4j
@Component
public class MQProducer {
    
    @Autowired
    private MqConfig mqConfig;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;
    
    @Value("${rocketmq.producer.group:ai-agent-orchestrator-producer-group}")
    private String producerGroup;
    
    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private int sendMessageTimeout;
    
    @Value("${rocketmq.producer.retry-times-when-send-failed:3}")
    private int retryTimesWhenSendFailed;
    
    private DefaultMQProducer producer;
    
    /**
     * 初始化生产者
     */
    @PostConstruct
    public void initProducer() {
        MqConfig.ProducerConfig producerConfig = mqConfig.getProducer();
        
        log.info("初始化RocketMQ Producer: nameServer={}, group={}", nameServer, producerGroup);
        
        try {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(sendMessageTimeout);
            producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
            producer.setMaxMessageSize(4 * 1024 * 1024); // 4MB
            
            producer.start();
            
            log.info("RocketMQ Producer初始化成功");
        } catch (MQClientException e) {
            log.error("RocketMQ Producer初始化失败", e);
            throw new RuntimeException("MQ Producer初始化失败", e);
        }
    }
    
    /**
     * 发送消息
     * 
     * @param topic 主题
     * @param msg 消息内容
     * @return 是否发送成功
     */
    public boolean send(String topic, String msg) {
        long startTime = System.currentTimeMillis();
        
        if (producer == null) {
            log.error("MQ Producer未初始化，请先调用initProducer()");
            reportSendMetrics(topic, startTime, false, "producer_not_initialized");
            return false;
        }
        
        try {
            Message message = new Message(topic, msg.getBytes(StandardCharsets.UTF_8));
            SendResult sendResult = producer.send(message);
            
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                // 上报成功指标
                reportSendMetrics(topic, startTime, true, null);
                log.debug("消息发送成功: topic={}, msgId={}", topic, sendResult.getMsgId());
                return true;
            } else {
                // 上报失败指标
                reportSendMetrics(topic, startTime, false, sendResult.getSendStatus().name());
                log.error("消息发送失败: topic={}, status={}", topic, sendResult.getSendStatus());
                return false;
            }
            
        } catch (Exception e) {
            // 上报异常指标
            reportSendMetrics(topic, startTime, false, e.getClass().getSimpleName());
            log.error("MQ发送异常: topic={}, error={}", topic, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 上报MQ发送指标
     */
    private void reportSendMetrics(String topic, long startTime, boolean success, String errorType) {
        if (metricsClient == null) {
            return;
        }
        
        try {
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, String> tags = new HashMap<>();
            tags.put("topic", topic);
            tags.put("status", success ? "success" : "failed");
            
            if (errorType != null) {
                tags.put("error_type", errorType);
            }
            
            // 上报耗时
            metricsClient.timing("mq_producer_send_duration", duration, tags);
            
            // 上报计数
            metricsClient.incrementCounter("mq_producer_send_total", tags);
            
        } catch (Exception e) {
            log.warn("上报MQ发送指标失败", e);
        }
    }
    
    /**
     * 关闭生产者
     */
    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            try {
                producer.shutdown();
                log.info("RocketMQ Producer已关闭");
            } catch (Exception e) {
                log.error("关闭Producer时发生异常", e);
            }
        }
    }
}

