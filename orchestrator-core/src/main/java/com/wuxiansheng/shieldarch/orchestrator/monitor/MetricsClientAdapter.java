package com.wuxiansheng.shieldarch.orchestrator.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 指标客户端适配器
 * 
 * 使用 Prometheus + Micrometer 进行指标上报
 */
@Slf4j
@Component
public class MetricsClientAdapter {

    @Autowired(required = false)
    private PrometheusMetricsClient prometheusMetricsClient;

    /**
     * 计数型指标（CounterN）
     */
    public void count(String metric, long value, Map<String, String> tags) {
        if (prometheusMetricsClient != null) {
            prometheusMetricsClient.count(metric, value, tags);
        }
    }

    /**
     * 累加计数（Counter, delta=1）
     */
    public void increment(String metric, Map<String, String> tags) {
        if (prometheusMetricsClient != null) {
            prometheusMetricsClient.increment(metric, tags);
        }
    }

    /**
     * 记录执行时长（毫秒）
     */
    public void timing(String metric, long durationMs, Map<String, String> tags) {
        if (prometheusMetricsClient != null) {
            prometheusMetricsClient.timing(metric, durationMs, tags);
        }
    }

    /**
     * 记录 RPC 调用指标
     */
    public void recordRpcMetric(String method, String sourceUniqueId, String businessName, 
                               long durationMs, int responseCode) {
        if (prometheusMetricsClient != null) {
            prometheusMetricsClient.recordRpcMetric(method, sourceUniqueId, businessName, durationMs, responseCode);
        }
    }

    /**
     * 记录 Gauge 指标
     */
    public void recordGauge(String metric, long value, Map<String, String> tags) {
        if (prometheusMetricsClient != null) {
            prometheusMetricsClient.recordGauge(metric, value, tags);
        }
    }

    /**
     * 累加计数（Counter）
     */
    public void incrementCounter(String metric, Map<String, String> tags) {
        increment(metric, tags);
    }
}

