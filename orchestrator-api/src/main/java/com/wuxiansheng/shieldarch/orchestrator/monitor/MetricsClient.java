package com.wuxiansheng.shieldarch.orchestrator.monitor;

import java.util.Map;

/**
 * 指标客户端接口
 * 
 * 用于上报监控指标到Prometheus
 */
public interface MetricsClient {
    /**
     * 计数型指标（CounterN）
     */
    void count(String metric, long value, Map<String, String> tags);

    /**
     * 累加计数（Counter, delta=1）
     */
    void increment(String metric, Map<String, String> tags);

    /**
     * 记录执行时长（毫秒）
     */
    void timing(String metric, long durationMs, Map<String, String> tags);

    /**
     * 记录 RPC 调用指标
     */
    void recordRpcMetric(String method, String sourceUniqueId, String businessName, 
                         long durationMs, int responseCode);

    /**
     * 记录 Gauge 指标
     */
    void recordGauge(String metric, long value, Map<String, String> tags);

    /**
     * 累加计数（Counter）
     */
    void incrementCounter(String metric, Map<String, String> tags);
}

