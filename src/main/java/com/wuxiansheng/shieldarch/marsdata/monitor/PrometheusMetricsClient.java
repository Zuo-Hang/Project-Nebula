package com.wuxiansheng.shieldarch.marsdata.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus 指标客户端
 * 
 * 使用 Micrometer + Prometheus 进行指标上报
 * 提供统一的指标上报接口
 */
@Slf4j
@Component
public class PrometheusMetricsClient {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    
    // 缓存 Meter 实例，避免重复创建
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    // Gauge 使用 AtomicLong 存储值
    private final Map<String, java.util.concurrent.atomic.AtomicLong> gaugeValueCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (meterRegistry == null) {
            log.warn("[PrometheusMetricsClient] MeterRegistry 未找到，Prometheus 指标上报将被禁用");
            this.enabled.set(false);
            return;
        }
        this.enabled.set(true);
        log.info("[PrometheusMetricsClient] Prometheus 指标客户端初始化成功");
    }

    /**
     * 计数型指标（CounterN）
     * 
     * @param metric 指标名称
     * @param value 计数值
     * @param tags 标签（Map 格式）
     */
    public void count(String metric, long value, Map<String, String> tags) {
        if (!enabled.get() || meterRegistry == null) {
            return;
        }
        try {
            Counter counter = getOrCreateCounter(metric, tags);
            counter.increment(value);
        } catch (Exception e) {
            log.warn("[PrometheusMetricsClient] 发送 count 指标失败: metric={}, value={}", metric, value, e);
        }
    }

    /**
     * 累加计数（Counter, delta=1）
     * 
     * @param metric 指标名称
     * @param tags 标签
     */
    public void increment(String metric, Map<String, String> tags) {
        count(metric, 1, tags);
    }

    /**
     * 记录执行时长（毫秒）
     * 
     * @param metric 指标名称
     * @param durationMs 耗时（毫秒）
     * @param tags 标签
     */
    public void timing(String metric, long durationMs, Map<String, String> tags) {
        if (!enabled.get() || meterRegistry == null) {
            return;
        }
        try {
            Timer timer = getOrCreateTimer(metric, tags);
            timer.record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[PrometheusMetricsClient] 发送 timing 指标失败: metric={}, durationMs={}", metric, durationMs, e);
        }
    }

    /**
     * 记录 RPC 调用指标
     * 
     * @param method 方法名
     * @param sourceUniqueId 源唯一ID
     * @param businessName 业务名称
     * @param durationMs 耗时（毫秒）
     * @param responseCode 响应码（0表示成功，非0表示失败）
     */
    public void recordRpcMetric(String method, String sourceUniqueId, String businessName, 
                               long durationMs, int responseCode) {
        if (!enabled.get() || meterRegistry == null) {
            return;
        }
        try {
            // 构建标签
            Map<String, String> tags = Map.of(
                "method", method != null ? method : "unknown",
                "source", sourceUniqueId != null ? sourceUniqueId : "unknown",
                "business", businessName != null ? businessName : "unknown",
                "status", responseCode == 0 ? "success" : "fail"
            );
            
            // 记录执行时间
            Timer timer = getOrCreateTimer(method + "_duration_ms", tags);
            timer.record(durationMs, TimeUnit.MILLISECONDS);
            
            // 记录成功/失败计数
            Counter counter = getOrCreateCounter(method + "_total", tags);
            counter.increment();
        } catch (Exception e) {
            log.warn("[PrometheusMetricsClient] 发送RPC指标失败: method={}, error={}", method, e.getMessage());
        }
    }

    /**
     * 记录 Gauge 指标（瞬时值）
     * 
     * @param metric 指标名称
     * @param value 值
     * @param tags 标签
     */
    public void recordGauge(String metric, long value, Map<String, String> tags) {
        if (!enabled.get() || meterRegistry == null) {
            return;
        }
        try {
            String gaugeKey = buildGaugeKey(metric, tags);
            // 获取或创建 AtomicLong 存储值
            java.util.concurrent.atomic.AtomicLong gaugeValue = gaugeValueCache.computeIfAbsent(gaugeKey, k -> {
                // 创建 Gauge，使用 AtomicLong 作为值源
                java.util.concurrent.atomic.AtomicLong atomicValue = new java.util.concurrent.atomic.AtomicLong(value);
                Gauge.builder(metric, atomicValue, AtomicLong::get)
                    .tags(buildTagsArray(tags))
                    .register(meterRegistry);
                return atomicValue;
            });
            // 更新值
            gaugeValue.set(value);
        } catch (Exception e) {
            log.warn("[PrometheusMetricsClient] 发送Gauge指标失败: metric={}, value={}", metric, value, e);
        }
    }

    /**
     * 累加计数（Counter）
     * 
     * @param metric 指标名称
     * @param tags 标签
     */
    public void incrementCounter(String metric, Map<String, String> tags) {
        increment(metric, tags);
    }

    /**
     * 获取或创建 Counter
     */
    private Counter getOrCreateCounter(String metric, Map<String, String> tags) {
        String key = buildCounterKey(metric, tags);
        return counterCache.computeIfAbsent(key, k -> {
            return Counter.builder(metric)
                .tags(buildTagsArray(tags))
                .register(meterRegistry);
        });
    }

    /**
     * 获取或创建 Timer
     */
    private Timer getOrCreateTimer(String metric, Map<String, String> tags) {
        String key = buildTimerKey(metric, tags);
        return timerCache.computeIfAbsent(key, k -> {
            return Timer.builder(metric)
                .tags(buildTagsArray(tags))
                .register(meterRegistry);
        });
    }

    /**
     * 构建 Counter 缓存键
     */
    private String buildCounterKey(String metric, Map<String, String> tags) {
        return "counter:" + metric + ":" + buildTagsString(tags);
    }

    /**
     * 构建 Timer 缓存键
     */
    private String buildTimerKey(String metric, Map<String, String> tags) {
        return "timer:" + metric + ":" + buildTagsString(tags);
    }

    /**
     * 构建 Gauge 缓存键
     */
    private String buildGaugeKey(String metric, Map<String, String> tags) {
        return "gauge:" + metric + ":" + buildTagsString(tags);
    }

    /**
     * 构建标签字符串（用于缓存键）
     */
    private String buildTagsString(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * 构建标签数组（用于 Micrometer）
     */
    private String[] buildTagsArray(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.entrySet().stream()
            .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
            .toArray(String[]::new);
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled.get();
    }
}

