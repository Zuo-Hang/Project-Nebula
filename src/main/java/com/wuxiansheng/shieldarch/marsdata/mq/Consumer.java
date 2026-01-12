package com.wuxiansheng.shieldarch.marsdata.mq;

import com.wuxiansheng.shieldarch.marsdata.config.MqConfig;
import com.wuxiansheng.shieldarch.marsdata.llm.MessageHandler;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息队列消费者
 * 使用RocketMQ替代Carrera
 */
@Slf4j
@Component
public class Consumer {
    
    @Autowired
    private MqConfig mqConfig;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    @Autowired
    private MessageHandler messageHandler;
    
    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;
    
    /**
     * 全局消费者列表
     */
    private final List<DefaultMQPushConsumer> globalConsumers = new java.util.ArrayList<>();
    
    /**
     * 消费者映射表（topic -> consumer）
     */
    private final Map<String, DefaultMQPushConsumer> consumerMap = new ConcurrentHashMap<>();
    
    /**
     * 启动消费者
     */
    @PostConstruct
    public void startConsumer() {
        Map<String, MqConfig.ConsumerConfig> consumerConfigs = mqConfig.getConsumers();
        
        if (consumerConfigs == null || consumerConfigs.isEmpty()) {
            log.warn("未配置MQ消费者，跳过启动");
            return;
        }
        
        log.info("启动消息队列消费者，共 {} 个消费者配置", consumerConfigs.size());
        
        for (Map.Entry<String, MqConfig.ConsumerConfig> entry : consumerConfigs.entrySet()) {
            String topic = entry.getKey();
            MqConfig.ConsumerConfig config = entry.getValue();
            
            log.info("初始化消费者: topic={}, group={}, threadNum={}, batchSize={}", 
                topic, config.getGroup(), config.getGoroutineNum(), config.getBatchNum());
            
            try {
                DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(config.getGroup());
                consumer.setNamesrvAddr(nameServer);
                consumer.setConsumeThreadMin(1);
                consumer.setConsumeThreadMax(config.getGoroutineNum());
                consumer.setConsumeMessageBatchMaxSize(config.getBatchNum());
                consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
                
                // 订阅topic
                consumer.subscribe(topic, "*");
                
                // 注册消息监听器
                consumer.registerMessageListener(new MessageListenerConcurrently() {
                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(
                            List<MessageExt> messages,
                            ConsumeConcurrentlyContext context) {
                        return processMessages(messages, topic);
                    }
                });
                
                consumer.start();
                
                globalConsumers.add(consumer);
                consumerMap.put(topic, consumer);
                
                log.info("消费者启动成功: topic={}", topic);
                
            } catch (MQClientException e) {
                log.error("启动消费者失败: topic={}, error={}", topic, e.getMessage(), e);
                // 继续启动其他消费者，不中断整个流程
            }
        }
        
        log.info("所有消费者启动完成，共 {} 个消费者", globalConsumers.size());
    }
    
    /**
     * 处理消息
     */
    private ConsumeConcurrentlyStatus processMessages(List<MessageExt> messages, String topic) {
        for (MessageExt messageExt : messages) {
            try {
                // 监控MQ
                monitorMQ(topic, messageExt);
                
                // 获取消息内容
                String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
                String keys = messageExt.getKeys();
                String tags = messageExt.getTags();
                
                log.info("Consumer recv_msg body: {}, keys: {}, tags: {}, topic: {}", 
                    body, keys, tags, topic);
                
                // 处理消息
                try {
                    messageHandler.handleMsg(body);
                    
                } catch (MessageHandler.MsgExpiredException e) {
                    // 消息过期，不再重试
                    log.warn("消息已过期，不再重试: {}", e.getMessage());
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } catch (MessageHandler.MsgFormatException e) {
                    // 消息格式错误，不再无限重试
                    log.warn("消息格式错误，不再重试: {}", e.getMessage());
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } catch (MessageHandler.ReasonFailException e) {
                    // 推理失败，需要重试
                    log.error("推理失败，需要重试: {}", e.getMessage());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    
                } catch (Exception e) {
                    log.error("处理消息失败: body={}, keys={}, tags={}, topic={}, error={}", 
                        body, keys, tags, topic, e.getMessage(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                
            } catch (Exception e) {
                log.error("Consumer处理消息失败: topic={}, error={}", topic, e.getMessage(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
    
    /**
     * 监控MQ指标
     */
    private void monitorMQ(String topic, MessageExt messageExt) {
        if (metricsClient == null) {
            return;
        }
        
        try {
            metricsClient.incrementCounter("ddmq_req", Map.of("topic", topic));
            
            // 检查重试次数
            int reconsumeTimes = messageExt.getReconsumeTimes();
            if (reconsumeTimes > 0) {
                metricsClient.incrementCounter("ddmq_req_retry", Map.of("retry", String.valueOf(reconsumeTimes)));
            }
        } catch (Exception e) {
            log.warn("监控MQ指标失败", e);
        }
    }
    
    /**
     * 停止消费者
     */
    @PreDestroy
    public void stopConsumer() {
        log.info("正在停止消息队列消费者...");
        
        for (DefaultMQPushConsumer consumer : globalConsumers) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("停止消费者失败", e);
            }
        }
        
        globalConsumers.clear();
        consumerMap.clear();
        
        log.info("所有消费者已停止");
    }
}
