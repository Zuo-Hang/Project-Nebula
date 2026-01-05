package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频抽帧链路配置
 * 对应 Go 版本的 configs.VideoFrameExtractionConfig
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoFrameExtractionConfig {

    @JsonProperty("input")
    private IOConfig input = new IOConfig();

    @JsonProperty("output")
    private IOConfig output = new IOConfig();

    @JsonProperty("frame_interval")
    private double frameInterval;

    @JsonProperty("max_workers")
    private int maxWorkers;

    @JsonProperty("max_concurrent_tasks")
    private int maxConcurrentTasks = 1;

    @JsonProperty("page_classify_confidence_threshold")
    private double pageClassifyConfidenceThreshold = 0.8;

    @JsonProperty("page_classify_case_sensitive")
    private boolean pageClassifyCaseSensitive = false;

    @JsonProperty("pages")
    private List<PageConfig> pages = new ArrayList<>();

    @JsonProperty("parse")
    private VideoParseConfig parse;

    /**
     * IO配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IOConfig {
        @JsonProperty("storage")
        private String storage;

        @JsonProperty("path_list")
        private List<String> pathList = new ArrayList<>();

        @JsonProperty("path")
        private String path;
    }

    /**
     * 页面配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageConfig {
        private String pageType; // 通过解析时动态设置

        @JsonProperty("classify")
        private PageClassifyConfig classify = new PageClassifyConfig();

        @JsonProperty("dedup")
        private PageDedupConfig dedup = new PageDedupConfig();
    }

    /**
     * 页面分类配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageClassifyConfig {
        @JsonProperty("keywords")
        private List<String> keywords = new ArrayList<>();

        @JsonProperty("exclude")
        private List<String> exclude = new ArrayList<>();

        @JsonProperty("min_matches")
        private int minMatches = 1;

        @JsonProperty("verify_regexes")
        private List<String> verifyRegexes = new ArrayList<>();
    }

    /**
     * 页面去重配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageDedupConfig {
        @JsonProperty("strategy_class")
        private String strategyClass;

        @JsonProperty("rule")
        private String rule;

        @JsonProperty("id")
        private PageDedupIDConfig id;

        @JsonProperty("param")
        private Map<String, String> param = new HashMap<>();

        @JsonProperty("params")
        private Map<String, Object> params = new HashMap<>();
    }

    /**
     * 页面去重ID生成配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageDedupIDConfig {
        @JsonProperty("mach")
        private String mach;

        @JsonProperty("strategy_class")
        private String strategyClass;
    }

    /**
     * 视频元数据解析配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoParseConfig {
        @JsonProperty("filename")
        private VideoFilenameConfig filename;

        @JsonProperty("normalize")
        private VideoNormalizeConfig normalize;

        @JsonProperty("validation")
        private VideoValidationConfig validation;
    }

    /**
     * 文件名解析配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoFilenameConfig {
        @JsonProperty("delimiter")
        private String delimiter;

        @JsonProperty("tokens_order")
        private List<String> tokensOrder = new ArrayList<>();

        @JsonProperty("overrides")
        private Map<String, VideoFilenameConfig> overrides = new HashMap<>();
    }

    /**
     * 标准化配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoNormalizeConfig {
        // 注意：city_suffix 和 supplier_map_ref 已硬编码在代码中，不再需要配置
    }

    /**
     * 视频元数据验证配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoValidationConfig {
        @JsonProperty("check_city_match")
        private boolean checkCityMatch;
    }
}

