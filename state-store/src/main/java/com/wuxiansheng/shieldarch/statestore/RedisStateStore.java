package com.wuxiansheng.shieldarch.statestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis状态存储实现
 * 
 * 以TaskId为Key，存储整个任务状态机的快照（JSON序列化）
 * 这是实现Exactly-once和断点续传的核心
 */
@Slf4j
@Component
public class RedisStateStore implements com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateStore {
    
    @Autowired(required = false)
    private RedisWrapper redisWrapper;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    /**
     * Redis键前缀
     */
    @Value("${orchestrator.state-store.redis-key-prefix:task_state:}")
    private String redisKeyPrefix;
    
    /**
     * 任务状态过期时间（秒），默认7天
     */
    @Value("${orchestrator.state-store.ttl-seconds:604800}")
    private long ttlSeconds;
    
    public RedisStateStore() {
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
    }
    
    /**
     * 保存任务状态到Redis
     * 
     * @param stateMachine 任务状态机
     */
    @Override
    public void save(TaskStateMachine stateMachine) {
        if (redisWrapper == null || !redisWrapper.isRedisEnabled()) {
            log.warn("Redis未启用，跳过状态持久化: taskId={}", stateMachine.getTaskId());
            return;
        }
        
        try {
            String taskId = stateMachine.getTaskId();
            String key = buildRedisKey(taskId);
            
            // 序列化为JSON
            String jsonValue = objectMapper.writeValueAsString(stateMachine);
            
            // 保存到Redis，设置过期时间
            redisWrapper.setEx(key, jsonValue, ttlSeconds);
            
            log.debug("任务状态已保存到Redis: taskId={}, key={}", taskId, key);
            
        } catch (Exception e) {
            log.error("保存任务状态到Redis失败: taskId={}, error={}", 
                stateMachine.getTaskId(), e.getMessage(), e);
            // 不抛出异常，允许任务继续执行
        }
    }
    
    /**
     * 从Redis加载任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态机，如果不存在返回null
     */
    @Override
    public TaskStateMachine load(String taskId) {
        if (redisWrapper == null || !redisWrapper.isRedisEnabled()) {
            log.warn("Redis未启用，无法加载任务状态: taskId={}", taskId);
            return null;
        }
        
        try {
            String key = buildRedisKey(taskId);
            
            // 从Redis获取
            String jsonValue = redisWrapper.get(key);
            
            if (jsonValue == null || jsonValue.isEmpty()) {
                log.debug("任务状态不存在: taskId={}, key={}", taskId, key);
                return null;
            }
            
            // 反序列化
            TaskStateMachine stateMachine = objectMapper.readValue(jsonValue, TaskStateMachine.class);
            
            log.debug("任务状态已从Redis加载: taskId={}, status={}", taskId, stateMachine.getStatus());
            
            return stateMachine;
            
        } catch (RedisWrapper.KeyNotFoundException e) {
            log.debug("任务状态不存在: taskId={}", taskId);
            return null;
        } catch (Exception e) {
            log.error("从Redis加载任务状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 删除任务状态
     * 
     * @param taskId 任务ID
     */
    public void delete(String taskId) {
        if (redisWrapper == null || !redisWrapper.isRedisEnabled()) {
            return;
        }
        
        try {
            String key = buildRedisKey(taskId);
            redisWrapper.getRedisClient().getBucket(key).delete();
            log.debug("任务状态已删除: taskId={}, key={}", taskId, key);
        } catch (Exception e) {
            log.error("删除任务状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }
    
    /**
     * 构建Redis键
     */
    private String buildRedisKey(String taskId) {
        return redisKeyPrefix + taskId;
    }
}

