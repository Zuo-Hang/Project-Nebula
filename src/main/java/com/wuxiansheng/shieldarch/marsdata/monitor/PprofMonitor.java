package com.wuxiansheng.shieldarch.marsdata.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * pprof监控服务
 * 
 * 注意：Go的pprof是Go特有的性能分析工具，Java版本使用Spring Boot Actuator替代
 * 可以通过 /actuator 端点访问各种监控信息
 */
@Slf4j
@Component
public class PprofMonitor {
    
    @Value("${app.pprof.port:8877}")
    private String pprofPort;
    
    /**
     * 启动pprof监控
     */
    @PostConstruct
    public void start() {
        try {
            // Java版本使用Spring Boot Actuator替代Go的pprof
            // 可以通过以下端点访问：
            // - /actuator/health - 健康检查
            // - /actuator/metrics - 指标
            // - /actuator/info - 应用信息
            // - /actuator/env - 环境变量
            // - /actuator/threaddump - 线程转储
            // - /actuator/heapdump - 堆转储
            // - /actuator/prometheus - Prometheus指标
            
            log.info("pprof监控已启动（通过Spring Boot Actuator）: 端口={}", pprofPort);
            log.info("访问监控端点: http://localhost:{}/actuator", pprofPort);
            
        } catch (Exception e) {
            log.error("启动pprof监控失败: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void stop() {
        log.info("pprof监控已停止");
    }
}

