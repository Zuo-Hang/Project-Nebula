package com.wuxiansheng.shieldarch.statestore;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis封装类
 */
@Slf4j
@Component
public class RedisWrapper {
    
    @Autowired(required = false)
    private RedissonClient redissonClient;
    
    // TODO: 迁移监控类后取消注释
    // @Autowired(required = false)
    // private MetricsClientAdapter metricsClient;

    /**
     * Redis键不存在错误
     */
    public static class KeyNotFoundException extends Exception {
        public KeyNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 初始化Redis客户端
     */
    public void initRedisClient() {
        if (redissonClient == null) {
            log.warn("Redis客户端未配置，Redis功能将不可用");
            return;
        }
        log.info("Redis客户端初始化成功");
    }

    /**
     * 检查Redis是否启用
     */
    public boolean isRedisEnabled() {
        return redissonClient != null;
    }

    /**
     * 获取Redis客户端
     */
    public RedissonClient getRedisClient() {
        return redissonClient;
    }

    /**
     * 获取键值
     * 
     * @param key 键
     * @return 值，如果键不存在抛出 KeyNotFoundException
     * @throws KeyNotFoundException 键不存在
     */
    public String get(String key) throws KeyNotFoundException {
        long begin = System.currentTimeMillis();
        int code = 0; // 0表示成功
        
        try {
            if (redissonClient == null) {
                code = 1;
                throw new KeyNotFoundException("redis client not initialized");
            }

            RBucket<String> bucket = redissonClient.getBucket(key);
            String value = bucket.get();
            
            if (value == null || value.isEmpty()) {
                code = 1;
                throw new KeyNotFoundException("redis: key not found");
            }
            
            return value;
        } catch (KeyNotFoundException e) {
            code = 1;
            throw e;
        } catch (Exception e) {
            code = 1;
            throw new KeyNotFoundException("redis get error: " + e.getMessage());
        } finally {
            // TODO: 迁移监控类后取消注释
            // 上报指标
            // if (metricsClient != null) {
            //     long latency = System.currentTimeMillis() - begin;
            //     metricsClient.recordRpcMetric("redis_get_req", "redis", "get", latency, code);
            // }
        }
    }

    /**
     * 设置键值并指定过期时间
     * 
     * @param key 键
     * @param value 值
     * @param ttl 过期时间（秒）
     */
    public void setEx(String key, String value, long ttl) {
        long begin = System.currentTimeMillis();
        int code = 0; // 0表示成功
        
        try {
            if (redissonClient == null) {
                code = 1;
                log.warn("Redis客户端未初始化，无法设置键值");
                return;
            }

            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            code = 1;
            log.error("Redis setEx error: {}", e.getMessage(), e);
        } finally {
            // TODO: 迁移监控类后取消注释
            // 上报指标
            // if (metricsClient != null) {
            //     long latency = System.currentTimeMillis() - begin;
            //     metricsClient.recordRpcMetric("redis_setex_req", "redis", "setex", latency, code);
            // }
        }
    }

    /**
     * 关闭Redis连接
     */
    public void close() {
        if (redissonClient != null) {
            redissonClient.shutdown();
            log.info("Redis客户端已关闭");
        }
    }
}

