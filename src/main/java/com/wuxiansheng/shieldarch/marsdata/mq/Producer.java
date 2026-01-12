package com.wuxiansheng.shieldarch.marsdata.mq;

import com.wuxiansheng.shieldarch.marsdata.config.MqConfig;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
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
import java.util.Map;

/**
 * 消息队列生产者
 * 使用RocketMQ替代Carrera
 */
@Slf4j
@Component
public class Producer {
    
    @Autowired
    private MqConfig mqConfig;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;
    
    @Value("${rocketmq.producer.group:llm-data-collect-producer-group}")
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
        
        log.info("初始化RocketMQ Producer: nameServer={}, group={}, topics={}", 
            nameServer, producerGroup, 
            producerConfig.getQuestBackstraceTopic() + "," + producerConfig.getOcrVideoCaptureTopic());
        
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
        if (producer == null) {
            log.error("MQ Producer未初始化，请先调用initProducer()");
            return false;
        }
        
        try {
            Message message = new Message(topic, msg.getBytes(StandardCharsets.UTF_8));
            SendResult sendResult = producer.send(message);
            
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                // 上报指标
                if (metricsClient != null) {
                    metricsClient.incrementCounter("ddmq_producer", Map.of("topic", topic));
                }
                
                log.debug("消息发送成功: topic={}, msgId={}", topic, sendResult.getMsgId());
                return true;
            } else {
                log.error("消息发送失败: topic={}, status={}", topic, sendResult.getSendStatus());
                return false;
            }
            
        } catch (Exception e) {
            log.error("MQ发送异常: topic={}, error={}", topic, e.getMessage(), e);
            return false;
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
