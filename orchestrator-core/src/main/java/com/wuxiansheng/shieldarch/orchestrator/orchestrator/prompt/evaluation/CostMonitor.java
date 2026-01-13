package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.evaluation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 成本监控器
 * 
 * 功能：
 * 1. Token使用量统计
 * 2. 成本计算（根据模型定价）
 * 3. 成本趋势分析
 * 
 * 支持多种模型定价策略
 */
@Slf4j
@Service
public class CostMonitor {
    
    /**
     * 输入Token价格（元/1K tokens）
     * 默认：GPT-4o定价
     */
    @Value("${prompt.cost.input-token-price:0.005}")
    private double inputTokenPrice;
    
    /**
     * 输出Token价格（元/1K tokens）
     * 默认：GPT-4o定价
     */
    @Value("${prompt.cost.output-token-price:0.015}")
    private double outputTokenPrice;
    
    /**
     * 成本指标
     */
    @Data
    public static class CostMetrics {
        /**
         * 总输入Token数
         */
        private long totalInputTokens;
        
        /**
         * 总输出Token数
         */
        private long totalOutputTokens;
        
        /**
         * 总Token数
         */
        private long totalTokens;
        
        /**
         * 平均输入Token数
         */
        private long avgInputTokens;
        
        /**
         * 平均输出Token数
         */
        private long avgOutputTokens;
        
        /**
         * 平均总Token数
         */
        private long avgTotalTokens;
        
        /**
         * 总成本（元）
         */
        private double totalCost;
        
        /**
         * 平均成本（元/请求）
         */
        private double avgCostPerRequest;
        
        /**
         * 输入成本（元）
         */
        private double inputCost;
        
        /**
         * 输出成本（元）
         */
        private double outputCost;
        
        /**
         * 请求总数
         */
        private int totalRequests;
    }
    
    /**
     * 计算成本指标
     * 
     * @param samples 样本数据
     * @return 成本指标
     */
    public CostMetrics calculateCost(List<TokenUsageSample> samples) {
        if (samples == null || samples.isEmpty()) {
            log.warn("样本数据为空，无法计算成本");
            return createEmptyMetrics();
        }
        
        CostMetrics metrics = new CostMetrics();
        metrics.setTotalRequests(samples.size());
        
        // 统计Token使用量
        List<Long> inputTokens = samples.stream()
            .filter(s -> s.getInputTokens() != null && s.getInputTokens() > 0)
            .map(s -> s.getInputTokens())
            .collect(Collectors.toList());
        
        List<Long> outputTokens = samples.stream()
            .filter(s -> s.getOutputTokens() != null && s.getOutputTokens() > 0)
            .map(s -> s.getOutputTokens())
            .collect(Collectors.toList());
        
        // 计算总和
        long totalInput = inputTokens.stream().mapToLong(Long::longValue).sum();
        long totalOutput = outputTokens.stream().mapToLong(Long::longValue).sum();
        long total = totalInput + totalOutput;
        
        metrics.setTotalInputTokens(totalInput);
        metrics.setTotalOutputTokens(totalOutput);
        metrics.setTotalTokens(total);
        
        // 计算平均值
        if (!inputTokens.isEmpty()) {
            metrics.setAvgInputTokens(totalInput / inputTokens.size());
        }
        if (!outputTokens.isEmpty()) {
            metrics.setAvgOutputTokens(totalOutput / outputTokens.size());
        }
        metrics.setAvgTotalTokens(total / samples.size());
        
        // 计算成本
        double inputCost = (totalInput / 1000.0) * inputTokenPrice;
        double outputCost = (totalOutput / 1000.0) * outputTokenPrice;
        double totalCost = inputCost + outputCost;
        
        metrics.setInputCost(inputCost);
        metrics.setOutputCost(outputCost);
        metrics.setTotalCost(totalCost);
        metrics.setAvgCostPerRequest(totalCost / samples.size());
        
        log.info("成本计算完成: 总成本={}元, 平均成本={}元/请求, 总Token={}", 
            totalCost, metrics.getAvgCostPerRequest(), total);
        
        return metrics;
    }
    
    /**
     * 计算指定业务和阶段的成本
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param samples 样本数据
     * @return 成本指标
     */
    public CostMetrics calculateCostByBizAndStage(
            String bizType, 
            String stage, 
            List<TokenUsageSample> samples) {
        
        CostMetrics metrics = calculateCost(samples);
        
        log.info("业务成本统计: bizType={}, stage={}, totalCost={}元, avgCost={}元/请求", 
            bizType, stage, metrics.getTotalCost(), metrics.getAvgCostPerRequest());
        
        return metrics;
    }
    
    /**
     * 计算成本趋势（按时间分组）
     * 
     * @param samples 样本数据（需要包含时间戳）
     * @param timeWindow 时间窗口（小时）
     * @return 成本趋势数据
     */
    public List<CostTrendPoint> calculateCostTrend(
            List<TokenUsageSample> samples, 
            int timeWindow) {
        
        // TODO: 实现按时间窗口分组统计
        // 这里简化实现，返回空列表
        log.debug("计算成本趋势: samples={}, timeWindow={}", samples.size(), timeWindow);
        return List.of();
    }
    
    /**
     * 创建空指标
     */
    private CostMetrics createEmptyMetrics() {
        CostMetrics metrics = new CostMetrics();
        metrics.setTotalRequests(0);
        metrics.setTotalInputTokens(0);
        metrics.setTotalOutputTokens(0);
        metrics.setTotalTokens(0);
        metrics.setAvgInputTokens(0);
        metrics.setAvgOutputTokens(0);
        metrics.setAvgTotalTokens(0);
        metrics.setTotalCost(0.0);
        metrics.setAvgCostPerRequest(0.0);
        metrics.setInputCost(0.0);
        metrics.setOutputCost(0.0);
        return metrics;
    }
    
    /**
     * Token使用样本
     */
    @lombok.Data
    public static class TokenUsageSample {
        /**
         * 输入Token数
         */
        private Long inputTokens;
        
        /**
         * 输出Token数
         */
        private Long outputTokens;
        
        /**
         * 时间戳（用于趋势分析）
         */
        private Long timestamp;
        
        /**
         * 业务类型
         */
        private String bizType;
        
        /**
         * Prompt阶段
         */
        private String stage;
    }
    
    /**
     * 成本趋势点
     */
    @lombok.Data
    public static class CostTrendPoint {
        /**
         * 时间窗口开始时间
         */
        private long windowStart;
        
        /**
         * 时间窗口结束时间
         */
        private long windowEnd;
        
        /**
         * 该时间窗口内的成本
         */
        private double cost;
        
        /**
         * 该时间窗口内的请求数
         */
        private int requestCount;
    }
}
