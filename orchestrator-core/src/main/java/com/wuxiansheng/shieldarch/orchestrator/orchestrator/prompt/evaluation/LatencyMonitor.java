package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.evaluation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 延迟监控器
 * 
 * 功能：
 * 1. 平均延迟统计
 * 2. P95、P99延迟统计
 * 3. 延迟分布分析
 * 4. 延迟趋势分析
 */
@Slf4j
@Service
public class LatencyMonitor {
    
    /**
     * 延迟指标
     */
    @Data
    public static class LatencyMetrics {
        /**
         * 平均延迟（毫秒）
         */
        private long avgLatency;
        
        /**
         * 最小延迟（毫秒）
         */
        private long minLatency;
        
        /**
         * 最大延迟（毫秒）
         */
        private long maxLatency;
        
        /**
         * P50延迟（中位数，毫秒）
         */
        private long p50Latency;
        
        /**
         * P95延迟（毫秒）
         */
        private long p95Latency;
        
        /**
         * P99延迟（毫秒）
         */
        private long p99Latency;
        
        /**
         * P999延迟（毫秒）
         */
        private long p999Latency;
        
        /**
         * 样本总数
         */
        private int totalSamples;
        
        /**
         * 延迟分布（可选）
         */
        private LatencyDistribution distribution;
    }
    
    /**
     * 延迟分布
     */
    @Data
    public static class LatencyDistribution {
        /**
         * <100ms的请求数
         */
        private int under100ms;
        
        /**
         * 100-500ms的请求数
         */
        private int between100And500ms;
        
        /**
         * 500-1000ms的请求数
         */
        private int between500And1000ms;
        
        /**
         * 1000-3000ms的请求数
         */
        private int between1000And3000ms;
        
        /**
         * >3000ms的请求数
         */
        private int over3000ms;
    }
    
    /**
     * 计算延迟指标
     * 
     * @param latencies 延迟列表（毫秒）
     * @return 延迟指标
     */
    public LatencyMetrics calculateLatency(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            log.warn("延迟数据为空，无法计算指标");
            return createEmptyMetrics();
        }
        
        LatencyMetrics metrics = new LatencyMetrics();
        metrics.setTotalSamples(latencies.size());
        
        // 排序（用于计算百分位数）
        List<Long> sortedLatencies = latencies.stream()
            .sorted()
            .collect(Collectors.toList());
        
        // 计算最小值、最大值
        metrics.setMinLatency(sortedLatencies.get(0));
        metrics.setMaxLatency(sortedLatencies.get(sortedLatencies.size() - 1));
        
        // 计算平均值
        long avgLatency = sortedLatencies.stream()
            .mapToLong(Long::longValue)
            .sum() / sortedLatencies.size();
        metrics.setAvgLatency(avgLatency);
        
        // 计算百分位数
        metrics.setP50Latency(calculatePercentile(sortedLatencies, 0.50));
        metrics.setP95Latency(calculatePercentile(sortedLatencies, 0.95));
        metrics.setP99Latency(calculatePercentile(sortedLatencies, 0.99));
        metrics.setP999Latency(calculatePercentile(sortedLatencies, 0.999));
        
        // 计算延迟分布
        LatencyDistribution distribution = calculateDistribution(sortedLatencies);
        metrics.setDistribution(distribution);
        
        log.info("延迟统计完成: avg={}ms, p95={}ms, p99={}ms, samples={}", 
            avgLatency, metrics.getP95Latency(), metrics.getP99Latency(), sortedLatencies.size());
        
        return metrics;
    }
    
    /**
     * 计算百分位数
     */
    private long calculatePercentile(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) {
            return 0;
        }
        
        int index = (int) Math.ceil(sortedLatencies.size() * percentile) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
    
    /**
     * 计算延迟分布
     */
    private LatencyDistribution calculateDistribution(List<Long> latencies) {
        LatencyDistribution distribution = new LatencyDistribution();
        
        for (Long latency : latencies) {
            if (latency < 100) {
                distribution.setUnder100ms(distribution.getUnder100ms() + 1);
            } else if (latency < 500) {
                distribution.setBetween100And500ms(distribution.getBetween100And500ms() + 1);
            } else if (latency < 1000) {
                distribution.setBetween500And1000ms(distribution.getBetween500And1000ms() + 1);
            } else if (latency < 3000) {
                distribution.setBetween1000And3000ms(distribution.getBetween1000And3000ms() + 1);
            } else {
                distribution.setOver3000ms(distribution.getOver3000ms() + 1);
            }
        }
        
        return distribution;
    }
    
    /**
     * 创建空指标
     */
    private LatencyMetrics createEmptyMetrics() {
        LatencyMetrics metrics = new LatencyMetrics();
        metrics.setTotalSamples(0);
        metrics.setAvgLatency(0);
        metrics.setMinLatency(0);
        metrics.setMaxLatency(0);
        metrics.setP50Latency(0);
        metrics.setP95Latency(0);
        metrics.setP99Latency(0);
        metrics.setP999Latency(0);
        return metrics;
    }
}
