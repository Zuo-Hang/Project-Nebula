package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.evaluation;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 综合评估器
 * 
 * 扩展评估维度：
 * 1. 准确性指标：准确率、召回率、F1分数
 * 2. 性能指标：平均延迟、P95延迟、P99延迟
 * 3. 成本指标：Token使用量、平均成本
 * 4. 安全性指标：毒性分数、安全违规次数
 */
@Slf4j
@Service
public class ComprehensiveEvaluator {
    
    /**
     * 评估结果
     */
    @Data
    public static class ComprehensiveEvaluationResult {
        // 准确性指标
        private double successRate;      // 成功率
        private double accuracy;          // 准确率
        private double precision;         // 精确率
        private double recall;            // 召回率
        private double f1Score;           // F1分数
        
        // 性能指标
        private long avgLatency;          // 平均延迟（毫秒）
        private long p95Latency;          // P95延迟（毫秒）
        private long p99Latency;          // P99延迟（毫秒）
        
        // 成本指标
        private long avgInputTokens;      // 平均输入Token数
        private long avgOutputTokens;     // 平均输出Token数
        private double avgCost;           // 平均成本（元）
        
        // 安全性指标
        private double avgToxicityScore;  // 平均毒性分数
        private int safetyViolations;     // 安全违规次数
        
        // 样本统计
        private int totalSamples;         // 总样本数
        private int successSamples;       // 成功样本数
        private int failureSamples;       // 失败样本数
    }
    
    /**
     * 评估Prompt效果
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param samples 样本数据
     * @return 综合评估结果
     */
    public ComprehensiveEvaluationResult evaluate(
            String bizType, 
            PromptStage stage, 
            List<EvaluationSample> samples) {
        
        if (samples == null || samples.isEmpty()) {
            log.warn("样本数据为空，无法评估: bizType={}, stage={}", bizType, stage);
            return createEmptyResult();
        }
        
        ComprehensiveEvaluationResult result = new ComprehensiveEvaluationResult();
        result.setTotalSamples(samples.size());
        
        // 1. 计算准确性指标
        calculateAccuracyMetrics(samples, result);
        
        // 2. 计算性能指标
        calculatePerformanceMetrics(samples, result);
        
        // 3. 计算成本指标
        calculateCostMetrics(samples, result);
        
        // 4. 计算安全性指标
        calculateSafetyMetrics(samples, result);
        
        log.info("综合评估完成: bizType={}, stage={}, accuracy={}, f1Score={}, avgCost={}", 
            bizType, stage, result.getAccuracy(), result.getF1Score(), result.getAvgCost());
        
        return result;
    }
    
    /**
     * 计算准确性指标
     */
    private void calculateAccuracyMetrics(
            List<EvaluationSample> samples, 
            ComprehensiveEvaluationResult result) {
        
        // 统计成功/失败
        long successCount = samples.stream()
            .filter(sample -> sample.isSuccess())
            .count();
        
        long failureCount = samples.size() - successCount;
        
        result.setSuccessSamples((int) successCount);
        result.setFailureSamples((int) failureCount);
        result.setSuccessRate((double) successCount / samples.size());
        
        // 计算准确率、召回率、F1（需要真实标签和预测标签）
        // 这里简化实现，实际应该从样本中获取
        long truePositives = samples.stream()
            .filter(s -> s.isSuccess() && s.getExpectedLabel() != null && 
                       s.getExpectedLabel().equals(s.getPredictedLabel()))
            .count();
        
        long falsePositives = samples.stream()
            .filter(s -> !s.isSuccess() && s.getPredictedLabel() != null)
            .count();
        
        long falseNegatives = samples.stream()
            .filter(s -> !s.isSuccess() && s.getExpectedLabel() != null)
            .count();
        
        // 精确率 = TP / (TP + FP)
        if (truePositives + falsePositives > 0) {
            result.setPrecision((double) truePositives / (truePositives + falsePositives));
        } else {
            result.setPrecision(0.0);
        }
        
        // 召回率 = TP / (TP + FN)
        if (truePositives + falseNegatives > 0) {
            result.setRecall((double) truePositives / (truePositives + falseNegatives));
        } else {
            result.setRecall(0.0);
        }
        
        // F1分数 = 2 * (Precision * Recall) / (Precision + Recall)
        if (result.getPrecision() + result.getRecall() > 0) {
            result.setF1Score(2 * result.getPrecision() * result.getRecall() / 
                (result.getPrecision() + result.getRecall()));
        } else {
            result.setF1Score(0.0);
        }
        
        // 准确率 = (TP + TN) / Total（简化：使用成功率）
        result.setAccuracy(result.getSuccessRate());
    }
    
    /**
     * 计算性能指标
     */
    private void calculatePerformanceMetrics(
            List<EvaluationSample> samples, 
            ComprehensiveEvaluationResult result) {
        
        List<Long> latencies = samples.stream()
            .filter(s -> s.getLatency() != null && s.getLatency() > 0)
            .map(s -> s.getLatency())
            .sorted()
            .collect(Collectors.toList());
        
        if (latencies.isEmpty()) {
            result.setAvgLatency(0);
            result.setP95Latency(0);
            result.setP99Latency(0);
            return;
        }
        
        // 平均延迟
        long avgLatency = latencies.stream()
            .mapToLong(Long::longValue)
            .sum() / latencies.size();
        result.setAvgLatency(avgLatency);
        
        // P95延迟
        int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        result.setP95Latency(latencies.get(Math.max(0, p95Index)));
        
        // P99延迟
        int p99Index = (int) Math.ceil(latencies.size() * 0.99) - 1;
        result.setP99Latency(latencies.get(Math.max(0, p99Index)));
    }
    
    /**
     * 计算成本指标
     */
    private void calculateCostMetrics(
            List<EvaluationSample> samples, 
            ComprehensiveEvaluationResult result) {
        
        List<Long> inputTokens = samples.stream()
            .filter(s -> s.getInputTokens() != null && s.getInputTokens() > 0)
            .map(s -> s.getInputTokens())
            .collect(Collectors.toList());
        
        List<Long> outputTokens = samples.stream()
            .filter(s -> s.getOutputTokens() != null && s.getOutputTokens() > 0)
            .map(s -> s.getOutputTokens())
            .collect(Collectors.toList());
        
        if (!inputTokens.isEmpty()) {
            long avgInput = inputTokens.stream().mapToLong(Long::longValue).sum() / inputTokens.size();
            result.setAvgInputTokens(avgInput);
        }
        
        if (!outputTokens.isEmpty()) {
            long avgOutput = outputTokens.stream().mapToLong(Long::longValue).sum() / outputTokens.size();
            result.setAvgOutputTokens(avgOutput);
        }
        
        // 计算平均成本（假设：输入Token 0.001元/1K，输出Token 0.002元/1K）
        double inputCost = result.getAvgInputTokens() * 0.001 / 1000.0;
        double outputCost = result.getAvgOutputTokens() * 0.002 / 1000.0;
        result.setAvgCost(inputCost + outputCost);
    }
    
    /**
     * 计算安全性指标
     */
    private void calculateSafetyMetrics(
            List<EvaluationSample> samples, 
            ComprehensiveEvaluationResult result) {
        
        List<Double> toxicityScores = samples.stream()
            .filter(s -> s.getToxicityScore() != null)
            .map(s -> s.getToxicityScore())
            .collect(Collectors.toList());
        
        if (!toxicityScores.isEmpty()) {
            double avgToxicity = toxicityScores.stream()
                .mapToDouble(Double::doubleValue)
                .sum() / toxicityScores.size();
            result.setAvgToxicityScore(avgToxicity);
        }
        
        // 统计安全违规次数
        long violations = samples.stream()
            .filter(s -> s.hasSafetyViolation())
            .count();
        result.setSafetyViolations((int) violations);
    }
    
    /**
     * 创建空结果
     */
    private ComprehensiveEvaluationResult createEmptyResult() {
        ComprehensiveEvaluationResult result = new ComprehensiveEvaluationResult();
        result.setTotalSamples(0);
        result.setSuccessSamples(0);
        result.setFailureSamples(0);
        result.setSuccessRate(0.0);
        result.setAccuracy(0.0);
        result.setPrecision(0.0);
        result.setRecall(0.0);
        result.setF1Score(0.0);
        return result;
    }
    
    /**
     * 评估样本
     */
    @lombok.Data
    public static class EvaluationSample {
        /**
         * 是否成功
         */
        private boolean success;
        
        /**
         * 预期标签
         */
        private String expectedLabel;
        
        /**
         * 预测标签
         */
        private String predictedLabel;
        
        /**
         * 延迟（毫秒）
         */
        private Long latency;
        
        /**
         * 输入Token数
         */
        private Long inputTokens;
        
        /**
         * 输出Token数
         */
        private Long outputTokens;
        
        /**
         * 毒性分数（0.0 - 1.0）
         */
        private Double toxicityScore;
        
        /**
         * 是否有安全违规
         */
        private boolean hasSafetyViolation;
    }
}
