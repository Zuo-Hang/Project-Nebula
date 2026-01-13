package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于多样性的示例选择器
 * 
 * 功能：
 * 1. 选择既相似又多样化的示例
 * 2. 避免选择过于相似的示例
 * 3. 提高Few-shot示例的覆盖度
 * 
 * 算法：Maximal Marginal Relevance (MMR)
 * 参考：LangChain MMRExampleSelector
 */
@Slf4j
@Component
public class DiversityExampleSelector implements ExampleSelector {
    
    /**
     * 候选示例池
     */
    private final List<Example> candidateExamples = new ArrayList<>();
    
    /**
     * 多样性权重（0.0 - 1.0）
     * - 0.0: 只考虑相似度
     * - 1.0: 只考虑多样性
     * - 0.5: 平衡相似度和多样性
     */
    private double diversityWeight = 0.5;
    
    /**
     * 相似度阈值
     */
    private double similarityThreshold = 0.0;
    
    /**
     * 选择示例（使用MMR算法）
     * 
     * @param query 查询文本
     * @param topK 返回的示例数量
     * @return 选中的示例列表
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
        
        // 计算所有示例与查询的相似度
        List<Example> scoredExamples = candidateExamples.stream()
            .map(example -> {
                double similarity = calculateSimilarity(query, example.getInput());
                example.setSimilarityScore(similarity);
                return example;
            })
            .filter(example -> example.getSimilarityScore() >= similarityThreshold)
            .collect(Collectors.toList());
        
        if (scoredExamples.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 使用MMR算法选择示例
        List<Example> selected = selectWithMMR(query, scoredExamples, topK);
        
        log.debug("多样性选择完成: query={}, topK={}, selected={}, diversityWeight={}", 
            query.substring(0, Math.min(50, query.length())), topK, selected.size(), diversityWeight);
        
        return selected;
    }
    
    /**
     * 使用MMR算法选择示例
     * 
     * MMR = λ * Sim(query, example) - (1 - λ) * max(Sim(example, selected))
     */
    private List<Example> selectWithMMR(
            String query, 
            List<Example> candidates, 
            int topK) {
        
        List<Example> selected = new ArrayList<>();
        List<Example> remaining = new ArrayList<>(candidates);
        
        // 选择第一个示例（与查询最相似的）
        if (!remaining.isEmpty()) {
            Example first = remaining.stream()
                .max(Comparator.comparing(Example::getSimilarityScore))
                .orElse(null);
            
            if (first != null) {
                selected.add(first);
                remaining.remove(first);
            }
        }
        
        // 迭代选择剩余示例
        while (selected.size() < topK && !remaining.isEmpty()) {
            Example best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            
            for (Example candidate : remaining) {
                // 计算与查询的相似度
                double relevance = candidate.getSimilarityScore() != null ? 
                    candidate.getSimilarityScore() : 0.0;
                
                // 计算与已选示例的最大相似度（多样性惩罚）
                double maxSimilarity = selected.stream()
                    .mapToDouble(sel -> calculateSimilarity(
                        candidate.getInput(), 
                        sel.getInput()))
                    .max()
                    .orElse(0.0);
                
                // MMR分数
                double mmrScore = diversityWeight * relevance - 
                    (1 - diversityWeight) * maxSimilarity;
                
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }
            
            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            } else {
                break;
            }
        }
        
        return selected;
    }
    
    /**
     * 计算相似度（简化实现：Jaccard相似度）
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return 0.0;
        }
        
        Set<Character> set1 = str1.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        Set<Character> set2 = str2.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);
        
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
     * 设置多样性权重
     */
    public void setDiversityWeight(double weight) {
        this.diversityWeight = Math.max(0.0, Math.min(1.0, weight));
        log.debug("设置多样性权重: {}", this.diversityWeight);
    }
    
    /**
     * 设置相似度阈值
     */
    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = threshold;
        log.debug("设置相似度阈值: {}", threshold);
    }
    
    /**
     * 获取候选示例数量
     */
    public int getCandidateCount() {
        return candidateExamples.size();
    }
}
