package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内容过滤器
 * 
 * 功能：
 * 1. 移除PII（个人身份信息）
 * 2. 移除敏感信息
 * 3. 转义特殊字符
 * 4. 内容脱敏
 * 
 * 参考：OWASP LLM Top 10 - LLM03: Training Data Poisoning
 */
@Slf4j
@Component
public class ContentFilter {
    
    /**
     * PII模式（个人身份信息）
     */
    private static final List<Pattern> PII_PATTERNS = Arrays.asList(
        // 身份证号（18位）
        Pattern.compile("\\d{17}[\\dXx]"),
        // 手机号（11位，1开头）
        Pattern.compile("1[3-9]\\d{9}"),
        // 银行卡号（16-19位）
        Pattern.compile("\\d{16,19}"),
        // 邮箱
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        // IP地址
        Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    );
    
    /**
     * 敏感关键词（可配置）
     */
    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
        "密码", "password", "secret", "密钥", "key",
        "token", "access_token", "api_key", "api_secret"
    );
    
    /**
     * 过滤内容
     * 
     * @param content 原始内容
     * @return 过滤后的内容
     */
    public String filterContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String filtered = content;
        
        // 1. 移除PII
        filtered = removePII(filtered);
        
        // 2. 移除敏感信息
        filtered = removeSensitiveInfo(filtered);
        
        // 3. 转义特殊字符
        filtered = escapeSpecialChars(filtered);
        
        return filtered;
    }
    
    /**
     * 移除PII（个人身份信息）
     */
    public String removePII(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String result = content;
        
        for (Pattern pattern : PII_PATTERNS) {
            result = pattern.matcher(result).replaceAll("[PII_REMOVED]");
        }
        
        return result;
    }
    
    /**
     * 移除敏感信息
     */
    public String removeSensitiveInfo(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String result = content;
        
        // 移除敏感关键词及其后面的内容（简单实现）
        for (String keyword : SENSITIVE_KEYWORDS) {
            // 匹配 "keyword: value" 或 "keyword=value" 格式
            Pattern pattern = Pattern.compile(
                keyword + "[:=]\\s*[^\\s]+", 
                Pattern.CASE_INSENSITIVE
            );
            result = pattern.matcher(result).replaceAll(keyword + ":[REDACTED]");
        }
        
        return result;
    }
    
    /**
     * 转义特殊字符
     * 
     * 防止特殊字符影响Prompt结构
     */
    public String escapeSpecialChars(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        return content
            .replace("\\", "\\\\")  // 反斜杠
            .replace("\"", "\\\"")  // 双引号
            .replace("\n", "\\n")   // 换行符
            .replace("\r", "\\r")   // 回车符
            .replace("\t", "\\t");  // 制表符
    }
    
    /**
     * 检查是否包含PII
     */
    public boolean containsPII(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        for (Pattern pattern : PII_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查是否包含敏感信息
     */
    public boolean containsSensitiveInfo(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        String lowerContent = content.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 内容过滤结果
     */
    @lombok.Data
    public static class FilterResult {
        /**
         * 原始内容
         */
        private String originalContent;
        
        /**
         * 过滤后的内容
         */
        private String filteredContent;
        
        /**
         * 是否包含PII
         */
        private boolean containsPII;
        
        /**
         * 是否包含敏感信息
         */
        private boolean containsSensitiveInfo;
        
        /**
         * 是否安全
         */
        public boolean isSafe() {
            return !containsPII && !containsSensitiveInfo;
        }
    }
    
    /**
     * 过滤内容并返回详细信息
     */
    public FilterResult filterWithDetails(String content) {
        FilterResult result = new FilterResult();
        result.setOriginalContent(content);
        
        if (content == null || content.isEmpty()) {
            result.setFilteredContent(content);
            result.setContainsPII(false);
            result.setContainsSensitiveInfo(false);
            return result;
        }
        
        // 检查是否包含PII和敏感信息
        result.setContainsPII(containsPII(content));
        result.setContainsSensitiveInfo(containsSensitiveInfo(content));
        
        // 执行过滤
        String filtered = filterContent(content);
        result.setFilteredContent(filtered);
        
        if (result.isSafe()) {
            log.debug("内容过滤完成: 安全");
        } else {
            log.warn("内容过滤完成: 检测到PII={}, 敏感信息={}", 
                result.isContainsPII(), result.isContainsSensitiveInfo());
        }
        
        return result;
    }
}
