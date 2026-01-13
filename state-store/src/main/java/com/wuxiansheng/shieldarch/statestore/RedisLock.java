package com.wuxiansheng.shieldarch.statestore;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁封装
 */
@Slf4j
@Component
public class RedisLock {
    
    @Autowired(required = false)
    private RedissonClient redissonClient;

    /**
     * 尝试获取锁
     * 
     * @param lockKey 锁的key
     * @param ttl 锁的过期时间（秒）
     * @return 锁对象，如果获取失败返回null
     */
    public RLock tryLock(String lockKey, long ttl) {
        if (redissonClient == null) {
            log.warn("Redis客户端未配置，无法获取分布式锁");
            return null;
        }

        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(0, ttl, TimeUnit.SECONDS)) {
                return lock;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断: {}", lockKey, e);
        }
        return null;
    }

    /**
     * 释放锁
     */
    public void unlock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.error("释放分布式锁失败", e);
            }
        }
    }
}

