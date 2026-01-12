package com.wuxiansheng.shieldarch.marsdata.scheduler;

import com.wuxiansheng.shieldarch.marsdata.io.RedisLock;
import com.wuxiansheng.shieldarch.marsdata.monitor.MetricsClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度器
 * - 使用 cron 表达式（支持秒级）
 * - 支持可选的分布式锁（LockedTask）
 * - 预留任务执行指标上报钩子
 */
@Slf4j
@Component
public class Scheduler {

    private final List<Task> tasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired(required = false)
    private RedisLock redisLock;

    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;

    /**
     * 初始化调度线程池
     */
    @PostConstruct
    public void init() {
        this.taskScheduler = new ThreadPoolTaskScheduler();
        // 固定大小线程池，和任务数量无关，避免 0 任务时创建失败
        this.taskScheduler.setPoolSize(4);
        this.taskScheduler.setThreadNamePrefix("mars-scheduler-");
        this.taskScheduler.initialize();
        log.info("Scheduler 初始化完成");
    }

    /**
     * 注册定时任务
     * <p>
     */
    public synchronized void register(Task task) {
        tasks.add(task);
        String schedule = task.getSchedule();
        log.info("注册定时任务: {}, cron 表达式: {}", task.getName(), schedule);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task),
                new CronTrigger(schedule)
        );
        futures.add(future);
    }

    /**
     * 执行单个任务，包含分布式锁与异常保护
     */
    private void executeTask(Task task) {
        Instant execTime = Instant.now();
        boolean locked = false;
        RLock lock = null;

        try {
            // 如果任务实现了 LockedTask，则尝试获取分布式锁
            if (task instanceof LockedTask && redisLock != null) {
                LockedTask lockedTask = (LockedTask) task;
                Duration ttl = lockedTask.getLockTTL();
                lock = redisLock.tryLock(lockedTask.getLockKey(), ttl.getSeconds());
                if (lock == null) {
                    // 未获取到锁，直接返回，不执行任务，也不记录失败
                    log.debug("定时任务 {} 未获取到分布式锁，跳过本次执行", task.getName());
                    return;
                }
                locked = true;
                reportTaskExecution(task.getName(), execTime);
            }

                            log.info("执行定时任务: {}", task.getName());
            Instant start = Instant.now();
                            task.execute();
            Instant end = Instant.now();
            reportTaskDuration(task.getName(), execTime, Duration.between(start, end));
                        } catch (Exception e) {
                            log.error("定时任务 {} 执行失败", task.getName(), e);
        } finally {
            if (locked && lock != null) {
                redisLock.unlock(lock);
            }
        }
    }

    /**
     * 任务执行计数指标钩子
     */
    protected void reportTaskExecution(String taskName, Instant execTime) {
        log.info("[Scheduler] 上报任务执行: task={}, execTime={}", taskName, execTime);
        if (metricsClient != null) {
            metricsClient.increment("scheduler_task", Map.of("task", taskName));
        }
    }

    /**
     * 任务执行时长指标钩子
     */
    protected void reportTaskDuration(String taskName, Instant execTime, Duration duration) {
        log.info("[Scheduler] 任务执行时长: task={}, execTime={}, duration={}", taskName, execTime, duration);
        if (metricsClient != null) {
            metricsClient.timing("scheduler_task_duration", duration.toMillis(), Map.of("task", taskName));
        }
    }

    /**
     * 停止所有定时任务
     */
    @PreDestroy
    public synchronized void stop() {
        log.info("正在停止定时任务调度器...");
        
        for (ScheduledFuture<?> future : futures) {
            if (future != null) {
            future.cancel(false);
        }
        }
        futures.clear();
        
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        
        log.info("定时任务调度器已停止");
    }
}

