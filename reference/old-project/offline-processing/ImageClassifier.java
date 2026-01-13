package com.wuxiansheng.shieldarch.marsdata.offline.image;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 图片分类器
 * 对应 Go 版本的 image.ImageClassifier
 */
@Slf4j
public class ImageClassifier {

    private final String pipelineName;
    private final boolean caseSensitive;
    private final double confidenceThreshold;
    private final VideoFrameExtractionConfigService configService;

    /**
     * 创建图片分类器
     * 
     * @param pipelineName 链路名称，用于获取对应的页面分类配置
     * @param configService 配置服务（可为 null）
     */
    public ImageClassifier(String pipelineName, VideoFrameExtractionConfigService configService) {
        this.pipelineName = pipelineName;
        this.configService = configService;
        
        VideoFrameExtractionConfig config = configService != null 
            ? configService.getVideoFrameExtractionConfig(pipelineName) 
            : null;
        
        if (config != null) {
            this.confidenceThreshold = config.getPageClassifyConfidenceThreshold();
            this.caseSensitive = config.isPageClassifyCaseSensitive();
        } else {
            this.confidenceThreshold = 0.8;
            this.caseSensitive = false;
        }
    }

    /**
     * 根据文本内容进行分类（基于当前 pipelineName 的配置）
     * 返回所有匹配的页面类型列表
     */
    public List<ClassificationMatch> classifyByText(String text) {
        if (configService == null) {
            return Collections.singletonList(createUnknownMatch());
        }

        VideoFrameExtractionConfig config = configService.getVideoFrameExtractionConfig(pipelineName);
        if (config == null || config.getPages() == null || config.getPages().isEmpty()) {
            return Collections.singletonList(createUnknownMatch());
        }

        return classifyByRules(text, config.getPages(), caseSensitive);
    }

    /**
     * 根据提供的页面规则判定文本所属页面类型
     */
    public static List<ClassificationMatch> classifyByRules(
            String text, 
            List<VideoFrameExtractionConfig.PageConfig> pages, 
            boolean caseSensitive) {
        
        if (pages == null || pages.isEmpty()) {
            return Collections.singletonList(createUnknownMatch());
        }

        String searchText = caseSensitive ? text : text.toLowerCase();
        List<ClassificationMatch> matches = new ArrayList<>();

        for (VideoFrameExtractionConfig.PageConfig pageCfg : pages) {
            // 跳过占位的 unknown 类型
            if ("unknown".equals(pageCfg.getPageType())) {
                continue;
            }

            VideoFrameExtractionConfig.PageClassifyConfig cls = pageCfg.getClassify();
            if (cls == null) {
                continue;
            }

            // 排除词命中则跳过该类型
            if (hasExcludeKeywords(searchText, cls.getExclude(), caseSensitive)) {
                continue;
            }

            // 验证正则表达式
            if (cls.getVerifyRegexes() != null && !cls.getVerifyRegexes().isEmpty()) {
                if (!matchAllRegexes(text, cls.getVerifyRegexes(), caseSensitive)) {
                    continue;
                }
            }

            // 获取匹配的关键词
            List<String> matched = getMatchedKeywords(searchText, cls.getKeywords(), caseSensitive);
            
            // 必须满足最小匹配数要求
            if (matched.size() >= cls.getMinMatches()) {
                ClassificationMatch match = new ClassificationMatch();
                match.setPageType(pageCfg.getPageType());
                match.setMatchedKeywords(matched);
                match.setMatchCount(matched.size());
                matches.add(match);
            }
        }

        if (matches.isEmpty()) {
            return Collections.singletonList(createUnknownMatch());
        }

        return matches;
    }

    private static ClassificationMatch createUnknownMatch() {
        ClassificationMatch match = new ClassificationMatch();
        match.setPageType("unknown");
        match.setMatchedKeywords(Collections.emptyList());
        match.setMatchCount(0);
        return match;
    }

    /**
     * 检查是否包含排除关键词
     */
    private static boolean hasExcludeKeywords(String text, List<String> excludeKeywords, boolean caseSensitive) {
        if (excludeKeywords == null || excludeKeywords.isEmpty()) {
            return false;
        }

        for (String keyword : excludeKeywords) {
            String searchKeyword = caseSensitive ? keyword : keyword.toLowerCase();
            if (text.contains(searchKeyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取匹配的关键词列表
     */
    private static List<String> getMatchedKeywords(String text, List<String> keywords, boolean caseSensitive) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> matched = new ArrayList<>();
        for (String keyword : keywords) {
            String searchKeyword = caseSensitive ? keyword : keyword.toLowerCase();
            if (text.contains(searchKeyword)) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    /**
     * 匹配所有正则表达式
     */
    private static boolean matchAllRegexes(String text, List<String> expressions, boolean caseSensitive) {
        if (expressions == null || expressions.isEmpty()) {
            return true;
        }

        for (String expr : expressions) {
            String pattern = expr.trim();
            if (pattern.isEmpty()) {
                continue;
            }

            // 如果不区分大小写且模式没有 (?i) 前缀，添加它
            if (!caseSensitive && !pattern.startsWith("(?i)") && !pattern.startsWith("(?-i)")) {
                pattern = "(?i)" + pattern;
            }

            try {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(text);
                if (!m.find()) {
                    return false;
                }
            } catch (Exception e) {
                log.warn("正则表达式编译失败: {}", pattern, e);
                return false;
            }
        }
        return true;
    }
}

