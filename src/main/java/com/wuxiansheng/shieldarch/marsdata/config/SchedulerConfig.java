package com.wuxiansheng.shieldarch.marsdata.config;

import com.wuxiansheng.shieldarch.marsdata.scheduler.Scheduler;
import com.wuxiansheng.shieldarch.marsdata.scheduler.tasks.IntegrityCheckTask;
import com.wuxiansheng.shieldarch.marsdata.scheduler.tasks.PriceFittingTask;
import com.wuxiansheng.shieldarch.marsdata.scheduler.tasks.VideoListTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 定时任务调度器配置
 */
@Slf4j
@Configuration
public class SchedulerConfig {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private IntegrityCheckTask integrityCheckTask;

    @Autowired
    private PriceFittingTask priceFittingTask;

    @Autowired
    private VideoListTask videoListTask;

    /**
     * 注册所有定时任务
     */
    @Bean
    public void registerTasks() {
        scheduler.register(priceFittingTask);
        scheduler.register(integrityCheckTask);
        scheduler.register(videoListTask);
        log.info("所有定时任务注册完成");
    }
}
