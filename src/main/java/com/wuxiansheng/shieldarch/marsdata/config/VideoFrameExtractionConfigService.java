package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.AppConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频抽帧链路配置服务
 * 对应 Go 版本的 configs.GetVideoFrameExtractionConfig
 */
@Slf4j
@Service
public class VideoFrameExtractionConfigService {

    @Autowired(required = false)
    private AppConfigService appConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 配置缓存（key: pipelineName, value: config）
     */
    private final Map<String, VideoFrameExtractionConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 环境变量（dev/prod）
     */
    private String environment = "dev";

    /**
     * 设置环境
     */
    public void setEnvironment(String env) {
        this.environment = env;
        configCache.clear(); // 环境变化时清空缓存
    }

    /**
     * 获取视频抽帧链路配置
     * 
     * @param pipelineName 链路名称
     * @return 配置对象，如果不存在则返回 null
     */
    public VideoFrameExtractionConfig getVideoFrameExtractionConfig(String pipelineName) {
        if (pipelineName == null || pipelineName.isEmpty()) {
            return null;
        }

        // 先从缓存获取
        VideoFrameExtractionConfig cached = configCache.get(pipelineName);
        if (cached != null) {
            return cached;
        }

        // 尝试从配置文件加载
        try {
            String configPath = getConfigPath(pipelineName);
            VideoFrameExtractionConfig config = loadFromFile(configPath, pipelineName);
            if (config != null) {
                configCache.put(pipelineName, config);
                return config;
            }
        } catch (Exception e) {
            log.warn("从配置文件加载链路配置失败: pipelineName={}, error={}", pipelineName, e.getMessage());
        }

        // 尝试从配置中心加载
        if (appConfigService != null) {
            try {
                VideoFrameExtractionConfig config = loadFromConfigService(pipelineName);
                if (config != null) {
                    configCache.put(pipelineName, config);
                    return config;
                }
            } catch (Exception e) {
                log.warn("从配置中心加载链路配置失败: pipelineName={}, error={}", pipelineName, e.getMessage());
            }
        }

        return null;
    }

    /**
     * 从文件加载配置
     */
    private VideoFrameExtractionConfig loadFromFile(String configPath, String pipelineName) {
        try {
            if (!Files.exists(Paths.get(configPath))) {
                log.debug("配置文件不存在: {}", configPath);
                return null;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(Files.newInputStream(Paths.get(configPath)));
            if (data == null) {
                return null;
            }

            // 转换为 JSON 然后反序列化（简化处理）
            String json = objectMapper.writeValueAsString(data);
            VideoFrameExtractionConfig config = objectMapper.readValue(json, VideoFrameExtractionConfig.class);
            
            // 解析 pages（YAML 中的特殊格式）
            if (data.containsKey("pages") && data.get("pages") instanceof List) {
                List<Map<String, Object>> pagesList = (List<Map<String, Object>>) data.get("pages");
                List<VideoFrameExtractionConfig.PageConfig> pageConfigs = new ArrayList<>();
                
                for (Map<String, Object> pageMap : pagesList) {
                    VideoFrameExtractionConfig.PageConfig pageConfig = parsePageConfig(pageMap);
                    if (pageConfig != null) {
                        pageConfigs.add(pageConfig);
                    }
                }
                config.setPages(pageConfigs);
            }

            return config;
        } catch (Exception e) {
            log.error("加载配置文件失败: path={}, error={}", configPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析页面配置（处理 YAML 中的 map 格式）
     */
    private VideoFrameExtractionConfig.PageConfig parsePageConfig(Map<String, Object> pageMap) {
        try {
            VideoFrameExtractionConfig.PageConfig pageConfig = new VideoFrameExtractionConfig.PageConfig();
            
            // 获取 pageType（map 的 key）
            String pageType = null;
            for (String key : pageMap.keySet()) {
                if (!key.equals("classify") && !key.equals("dedup")) {
                    pageType = key;
                    break;
                }
            }
            pageConfig.setPageType(pageType != null ? pageType : "unknown");

            // 解析 classify
            if (pageMap.containsKey("classify")) {
                Object classifyObj = pageMap.get("classify");
                if (classifyObj instanceof Map) {
                    String json = objectMapper.valueToTree(classifyObj).toString();
                    VideoFrameExtractionConfig.PageClassifyConfig classify = 
                        objectMapper.readValue(json, VideoFrameExtractionConfig.PageClassifyConfig.class);
                    pageConfig.setClassify(classify);
                }
            }

            // 解析 dedup
            if (pageMap.containsKey("dedup")) {
                Object dedupObj = pageMap.get("dedup");
                if (dedupObj instanceof Map) {
                    String json = objectMapper.valueToTree(dedupObj).toString();
                    VideoFrameExtractionConfig.PageDedupConfig dedup = 
                        objectMapper.readValue(json, VideoFrameExtractionConfig.PageDedupConfig.class);
                    pageConfig.setDedup(dedup);
                }
            }

            return pageConfig;
        } catch (Exception e) {
            log.error("解析页面配置失败", e);
            return null;
        }
    }

    /**
     * 从配置中心加载配置
     */
    private VideoFrameExtractionConfig loadFromConfigService(String pipelineName) {
        // TODO: 实现从配置中心加载配置的逻辑
        // 可以从配置中心的特定 namespace 和 key 读取配置
        return null;
    }

    /**
     * 获取配置文件路径
     */
    private String getConfigPath(String pipelineName) {
        // 配置文件命名规则: conf/video_frame_extraction_{pipelineName}.yaml
        return String.format("conf/video_frame_extraction_%s.yaml", pipelineName);
    }
}

