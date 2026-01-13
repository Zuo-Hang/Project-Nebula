package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于相似度的示例选择器
 * 
 * 功能：
 * 1. 使用向量相似度选择最相似的示例
 * 2. 支持自定义相似度阈值
 * 3. 支持多种相似度计算方法
 * 
 * 参考：LangChain SimilarityExampleSelector
 */
@Slf4j
@Component
public class SimilarityExampleSelector implements ExampleSelector {
    
    /**
     * 候选示例池
     */
    private final List<Example> candidateExamples = new ArrayList<>();
    
    /**
     * 相似度阈值（低于此值的示例不返回）
     */
    private double similarityThreshold = 0.0;
    
    /**
     * 是否使用向量相似度（如果为false，使用简单的文本相似度）
     */
    private boolean useVectorSimilarity = false;
    
    /**
     * 向量存储（可选，用于向量相似度计算）
     */
    // TODO: 集成向量数据库
    // private VectorStore vectorStore;
    
    /**
     * 选择示例
     * 
     * @param query 查询文本
     * @param topK 返回的示例数量
     * @return 选中的示例列表（按相似度降序排列）
     */
    @Override
    public List<Example> selectExamples(String query, int topK) {
        if (query == null || query.isEmpty()) {
            log.warn("查询文本为空，返回空示例列表");
            return new ArrayList<>();
        }
        
        if (candidateExamples.isEmpty()) {
            log.warn("候选示例池为空，返回空示例列表");
            return new ArrayList<>();
        }
        
        // 计算每个示例与查询的相似度
        List<Example> scoredExamples = candidateExamples.stream()
            .map(example -> {
                double similarity = calculateSimilarity(query, example.getInput());
                example.setSimilarityScore(similarity);
                return example;
            })
            .filter(example -> example.getSimilarityScore() >= similarityThreshold)
            .sorted((a, b) -> Double.compare(
                b.getSimilarityScore() != null ? b.getSimilarityScore() : 0.0,
                a.getSimilarityScore() != null ? a.getSimilarityScore() : 0.0
            ))
            .limit(topK)
            .collect(Collectors.toList());
        
        log.debug("选择示例完成: query={}, topK={}, selected={}, totalCandidates={}", 
            query.substring(0, Math.min(50, query.length())), topK, scoredExamples.size(), candidateExamples.size());
        
        return scoredExamples;
    }
    
    /**
     * 计算相似度
     * 
     * 简化实现：使用Jaccard相似度
     * 实际应该使用向量相似度（余弦相似度）
     */
    private double calculateSimilarity(String query, String example) {
        if (query == null || example == null) {
            return 0.0;
        }
        
        if (useVectorSimilarity) {
            // TODO: 使用向量相似度
            // return vectorStore.cosineSimilarity(query, example);
            return calculateJaccardSimilarity(query, example);
        } else {
            // 使用简单的文本相似度（Jaccard相似度）
            return calculateJaccardSimilarity(query, example);
        }
    }
    
    /**
     * 计算Jaccard相似度
     * 
     * Jaccard相似度 = |A ∩ B| / |A ∪ B|
     */
    private double calculateJaccardSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return 0.0;
        }
        
        // 将字符串转换为字符集合
        Set<Character> set1 = str1.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        Set<Character> set2 = str2.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        // 计算交集
        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        // 计算并集
        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);
        
        // Jaccard相似度
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * 添加示例
     */
    @Override
    public void addExample(Example example) {
        if (example != null) {
            candidateExamples.add(example);
            log.debug("添加示例: input={}", 
                example.getInput() != null ? 
                    example.getInput().substring(0, Math.min(50, example.getInput().length())) : "null");
        }
    }
    
    /**
     * 清除所有示例
     */
    @Override
    public void clearExamples() {
        candidateExamples.clear();
        log.info("清除所有示例");
    }
    
    /**
     * 设置相似度阈值
     */
    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = threshold;
        log.debug("设置相似度阈值: {}", threshold);
    }
    
    /**
     * 设置是否使用向量相似度
     */
    public void setUseVectorSimilarity(boolean useVectorSimilarity) {
        this.useVectorSimilarity = useVectorSimilarity;
        log.debug("设置使用向量相似度: {}", useVectorSimilarity);
    }
    
    /**
     * 获取候选示例数量
     */
    public int getCandidateCount() {
        return candidateExamples.size();
    }
}
