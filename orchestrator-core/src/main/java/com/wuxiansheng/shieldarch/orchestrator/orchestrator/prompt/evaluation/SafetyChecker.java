package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.evaluation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全性评估器
 * 
 * 功能：
 * 1. 毒性检测（Toxicity Detection）
 * 2. 内容安全检查
 * 3. 安全违规统计
 * 
 * 参考：Perspective API、OpenAI Moderation API
 */
@Slf4j
@Service
public class SafetyChecker {
    
    /**
     * 毒性关键词（简化实现，实际应该使用ML模型）
     */
    private static final List<String> TOXIC_KEYWORDS = Arrays.asList(
        "hate", "violence", "harassment", "self-harm", "sexual",
        "仇恨", "暴力", "骚扰", "自残", "色情"
    );
    
    /**
     * 暴力关键词
     */
    private static final List<String> VIOLENCE_KEYWORDS = Arrays.asList(
        "kill", "murder", "attack", "bomb", "weapon",
        "杀死", "谋杀", "攻击", "炸弹", "武器"
    );
    
    /**
     * 自残关键词
     */
    private static final List<String> SELF_HARM_KEYWORDS = Arrays.asList(
        "suicide", "self-harm", "cut", "overdose",
        "自杀", "自残", "割腕", "过量"
    );
    
    /**
     * 检查内容安全性
     * 
     * @param content 待检查的内容
     * @return 安全检查结果
     */
    public SafetyResult checkSafety(String content) {
        if (content == null || content.isEmpty()) {
            return createSafeResult();
        }
        
        SafetyResult result = new SafetyResult();
        result.setContent(content);
        
        String lowerContent = content.toLowerCase();
        
        // 1. 毒性检测
        double toxicityScore = detectToxicity(lowerContent);
        result.setToxicityScore(toxicityScore);
        result.setToxic(toxicityScore > 0.5);
        
        // 2. 暴力内容检测
        boolean containsViolence = containsKeywords(lowerContent, VIOLENCE_KEYWORDS);
        result.setContainsViolence(containsViolence);
        
        // 3. 自残内容检测
        boolean containsSelfHarm = containsKeywords(lowerContent, SELF_HARM_KEYWORDS);
        result.setContainsSelfHarm(containsSelfHarm);
        
        // 4. 综合判断
        boolean isSafe = toxicityScore <= 0.5 && !containsViolence && !containsSelfHarm;
        result.setSafe(isSafe);
        
        // 5. 安全类别
        if (!isSafe) {
            if (toxicityScore > 0.5) {
                result.addSafetyCategory("TOXICITY");
            }
            if (containsViolence) {
                result.addSafetyCategory("VIOLENCE");
            }
            if (containsSelfHarm) {
                result.addSafetyCategory("SELF_HARM");
            }
        }
        
        if (!isSafe) {
            log.warn("内容安全检查失败: toxicityScore={}, containsViolence={}, containsSelfHarm={}", 
                toxicityScore, containsViolence, containsSelfHarm);
        }
        
        return result;
    }
    
    /**
     * 检测毒性分数（0.0 - 1.0）
     * 
     * 简化实现：基于关键词匹配
     * 实际应该使用ML模型（如Perspective API）
     */
    private double detectToxicity(String content) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        
        // 统计毒性关键词出现次数
        long toxicCount = TOXIC_KEYWORDS.stream()
            .filter(keyword -> content.contains(keyword.toLowerCase()))
            .count();
        
        // 计算毒性分数（简化：关键词数量 / 总关键词数）
        double score = Math.min(1.0, (double) toxicCount / TOXIC_KEYWORDS.size());
        
        // 如果包含多个毒性关键词，提高分数
        if (toxicCount > 1) {
            score = Math.min(1.0, score * 1.5);
        }
        
        return score;
    }
    
    /**
     * 检查是否包含关键词
     */
    private boolean containsKeywords(String content, List<String> keywords) {
        return keywords.stream()
            .anyMatch(keyword -> content.contains(keyword.toLowerCase()));
    }
    
    /**
     * 批量检查安全性
     * 
     * @param contents 待检查的内容列表
     * @return 安全检查统计
     */
    public SafetyStatistics batchCheckSafety(List<String> contents) {
        SafetyStatistics statistics = new SafetyStatistics();
        statistics.setTotalSamples(contents.size());
        
        int safeCount = 0;
        int toxicCount = 0;
        int violenceCount = 0;
        int selfHarmCount = 0;
        double totalToxicityScore = 0.0;
        
        for (String content : contents) {
            SafetyResult result = checkSafety(content);
            
            if (result.isSafe()) {
                safeCount++;
            } else {
                if (result.isToxic()) {
                    toxicCount++;
                }
                if (result.isContainsViolence()) {
                    violenceCount++;
                }
                if (result.isContainsSelfHarm()) {
                    selfHarmCount++;
                }
            }
            
            totalToxicityScore += result.getToxicityScore();
        }
        
        statistics.setSafeCount(safeCount);
        statistics.setToxicCount(toxicCount);
        statistics.setViolenceCount(violenceCount);
        statistics.setSelfHarmCount(selfHarmCount);
        statistics.setSafetyRate((double) safeCount / contents.size());
        statistics.setAvgToxicityScore(totalToxicityScore / contents.size());
        
        log.info("批量安全检查完成: total={}, safe={}, toxic={}, violence={}, selfHarm={}", 
            contents.size(), safeCount, toxicCount, violenceCount, selfHarmCount);
        
        return statistics;
    }
    
    /**
     * 创建安全结果
     */
    private SafetyResult createSafeResult() {
        SafetyResult result = new SafetyResult();
        result.setSafe(true);
        result.setToxic(false);
        result.setContainsViolence(false);
        result.setContainsSelfHarm(false);
        result.setToxicityScore(0.0);
        return result;
    }
    
    /**
     * 安全检查结果
     */
    @Data
    public static class SafetyResult {
        /**
         * 内容
         */
        private String content;
        
        /**
         * 是否安全
         */
        private boolean safe;
        
        /**
         * 毒性分数（0.0 - 1.0）
         */
        private double toxicityScore;
        
        /**
         * 是否包含毒性内容
         */
        private boolean toxic;
        
        /**
         * 是否包含暴力内容
         */
        private boolean containsViolence;
        
        /**
         * 是否包含自残内容
         */
        private boolean containsSelfHarm;
        
        /**
         * 安全类别列表
         */
        private List<String> safetyCategories = new java.util.ArrayList<>();
        
        public void addSafetyCategory(String category) {
            if (!safetyCategories.contains(category)) {
                safetyCategories.add(category);
            }
        }
    }
    
    /**
     * 安全检查统计
     */
    @Data
    public static class SafetyStatistics {
        /**
         * 总样本数
         */
        private int totalSamples;
        
        /**
         * 安全样本数
         */
        private int safeCount;
        
        /**
         * 毒性样本数
         */
        private int toxicCount;
        
        /**
         * 暴力样本数
         */
        private int violenceCount;
        
        /**
         * 自残样本数
         */
        private int selfHarmCount;
        
        /**
         * 安全率（0.0 - 1.0）
         */
        private double safetyRate;
        
        /**
         * 平均毒性分数
         */
        private double avgToxicityScore;
    }
}
