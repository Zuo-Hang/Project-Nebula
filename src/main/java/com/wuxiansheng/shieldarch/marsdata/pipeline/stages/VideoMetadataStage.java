package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.CityMap;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 视频元数据解析阶段
 * 对应 Go 版本的 stages.VideoMetadataStage
 */
@Slf4j
public class VideoMetadataStage implements PipelineStage {

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    @Override
    public String name() {
        return "VideoMetadata";
    }

    @Override
    public String describe() {
        return "Parse video metadata from path and filename";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        String videoKey = pipelineCtx.getVideoKey();
        if (videoKey == null || videoKey.isEmpty()) {
            throw new Exception("VideoMetadataStage: VideoKey is required in context");
        }

        String linkName = pipelineCtx.getLinkName();
        if (linkName == null || linkName.isEmpty()) {
            throw new Exception("VideoMetadataStage: LinkName is required in context");
        }

        log.info("2. 解析视频元数据...");

        VideoFrameExtractionConfig config = configService != null 
            ? configService.getVideoFrameExtractionConfig(linkName) 
            : null;
        if (config == null) {
            throw new Exception("链路配置未找到: " + linkName);
        }

        // 使用配置解析元数据
        StageVideoMetadata meta = parseVideoMetadataWithConfig(videoKey, linkName, config);
        if (meta != null) {
            log.info("视频元数据: {} -> {}/{}", meta.getCityName(), meta.getSupplierName(), meta.getDriverName());
            pipelineCtx.set(com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey.VIDEO_METADATA, meta);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 使用指定的链路配置解析视频元数据
     */
    private StageVideoMetadata parseVideoMetadataWithConfig(
            String videoKey, String linkName, VideoFrameExtractionConfig config) {
        
        // 如果配置中没有 parse 配置，回退到 legacy 解析
        if (config == null || config.getParse() == null) {
            return parseVideoMetadataLegacy(videoKey);
        }

        String[] parts = videoKey.split("/");
        if (parts.length < 2) {
            return null;
        }
        String parentCity = parts[parts.length - 2];

        String base = new File(videoKey).getName();
        String nameNoExt = base.substring(0, base.lastIndexOf('.') > 0 ? base.lastIndexOf('.') : base.length());

        // 使用配置中的 parse 配置
        VideoFrameExtractionConfig.VideoParseConfig parseCfg = config.getParse();
        String cleanedName = normalizeFilename(nameNoExt);
        String delimiter = parseCfg.getFilename().getDelimiter();
        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = "-";
        }
        String[] fields = cleanedName.split(Pattern.quote(delimiter));
        if (fields.length < 3) {
            return null;
        }

        VideoFrameExtractionConfig.VideoFilenameConfig filenameCfg = parseCfg.getFilename();
        // TODO: 处理 overrides
        Map<String, String> parsed = parseFieldsByConfig(fields, filenameCfg.getTokensOrder(), nameNoExt);

        final String citySuffix = "市";
        String stdParentCity = normalizeCityName(parentCity, citySuffix);
        String stdFileCity = normalizeCityName(parsed.get("city_name"), citySuffix);
        String stdSupplier = standardizeSupplierName(parsed.get("supplier_name"));

        boolean cityIllegal = false;
        if (parseCfg.getValidation() != null && parseCfg.getValidation().isCheckCityMatch()) {
            cityIllegal = !stdParentCity.equals(stdFileCity);
        }

        StageVideoMetadata meta = new StageVideoMetadata();
        meta.setID(videoKey.substring(0, videoKey.lastIndexOf('.') > 0 ? videoKey.lastIndexOf('.') : videoKey.length()));
        meta.setVideoURL(videoKey);
        meta.setCityName(stdParentCity);
        meta.setSupplierName(stdSupplier);
        meta.setDriverName(parsed.get("driver_name"));
        meta.setFileCityName(stdFileCity);
        meta.setCityIllegal(cityIllegal);

        return meta;
    }

    /**
     * 遗留的元数据解析逻辑
     */
    private StageVideoMetadata parseVideoMetadataLegacy(String videoKey) {
        String[] parts = videoKey.split("/");
        if (parts.length < 2) {
            return null;
        }
        String parentCity = parts[parts.length - 2];

        String base = new File(videoKey).getName();
        String nameNoExt = base.substring(0, base.lastIndexOf('.') > 0 ? base.lastIndexOf('.') : base.length());
        String[] fields = nameNoExt.split("-");
        if (fields.length < 4) {
            return null;
        }

        String fileCity, supplier, driver;
        if (fields.length >= 4 && isEightDigit(fields[0])) {
            fileCity = fields[1];
            supplier = fields[2];
            driver = fields[3];
        } else {
            fileCity = fields[fields.length - 2];
            driver = fields[fields.length - 3];
            supplier = fields[fields.length - 4];
        }

        if (videoKey.contains("OCR_B_CC") && fields.length >= 5) {
            fileCity = fields[fields.length - 2];
            driver = fields[fields.length - 3];
            supplier = fields[fields.length - 4];
        }

        String stdParentCity = normalizeCityNameLegacy(parentCity);
        String stdFileCity = normalizeCityNameLegacy(fileCity);
        boolean cityIllegal = !stdParentCity.equals(stdFileCity);

        StageVideoMetadata meta = new StageVideoMetadata();
        meta.setID(videoKey.substring(0, videoKey.lastIndexOf('.') > 0 ? videoKey.lastIndexOf('.') : videoKey.length()));
        meta.setVideoURL(videoKey);
        meta.setCityName(stdParentCity);
        meta.setSupplierName(standardizeSupplierNameLegacy(supplier));
        meta.setDriverName(driver);
        meta.setFileCityName(stdFileCity);
        meta.setCityIllegal(cityIllegal);

        return meta;
    }

    /**
     * 标准化文件名
     */
    private String normalizeFilename(String text) {
        String result = text;
        // 规则1：将空格替换为连字符
        result = result.replace(" ", "-");
        // 规则2：清理全角连字符、加号
        result = result.replace("－", "-");
        result = result.replace("＋", "-");
        result = result.replace("+", "-");
        // 规则3：清理多余的连字符
        while (result.contains("--")) {
            result = result.replace("--", "-");
        }
        // 规则4：清理末尾的连字符
        if (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 根据配置解析字段
     */
    private Map<String, String> parseFieldsByConfig(String[] fields, List<String> tokensOrder, String nameNoExt) {
        Map<String, String> out = new HashMap<>();
        if (fields.length >= 4 && isEightDigit(fields[0])) {
            if (fields.length >= 4) {
                out.put("city_name", fields[1]);
                out.put("supplier_name", fields[2]);
                out.put("driver_name", fields[3]);
            }
            return out;
        }
        if (tokensOrder != null) {
            for (int i = 0; i < tokensOrder.size() && i < fields.length; i++) {
                out.put(tokensOrder.get(i), fields[i]);
            }
        }
        return out;
    }

    /**
     * 标准化城市名称
     */
    private String normalizeCityName(String name, String citySuffix) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (CityMap.getCityMap().containsKey(name)) {
            return name;
        }
        if (citySuffix != null && !citySuffix.isEmpty()) {
            String with = name + citySuffix;
            if (CityMap.getCityMap().containsKey(with)) {
                return with;
            }
        }
        return name;
    }

    /**
     * 标准化供应商名称
     */
    private String standardizeSupplierName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // TODO: 从配置中获取供应商映射表
        Map<String, String> similar = new HashMap<>();
        similar.put("星辉", "星徽出行");
        similar.put("星徽", "星徽出行");
        similar.put("曹操", "曹操出行");
        similar.put("T3", "T3出行");
        return similar.getOrDefault(name, name);
    }

    /**
     * 判断是否为8位数字
     */
    private boolean isEightDigit(String s) {
        if (s == null || s.length() != 8) {
            return false;
        }
        return s.matches("\\d{8}");
    }

    /**
     * 标准化城市名称（遗留逻辑）
     */
    private String normalizeCityNameLegacy(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (CityMap.getCityMap().containsKey(name)) {
            return name;
        }
        if (CityMap.getCityMap().containsKey(name + "市")) {
            return name + "市";
        }
        return name;
    }

    /**
     * 标准化供应商名称（遗留逻辑）
     */
    private String standardizeSupplierNameLegacy(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        Map<String, String> similar = new HashMap<>();
        similar.put("星辉", "星徽出行");
        similar.put("星徽", "星徽出行");
        similar.put("曹操", "曹操出行");
        similar.put("T3", "T3出行");
        return similar.getOrDefault(name, name);
    }

    /**
     * 视频元数据（内部使用）
     */
    @Data
    public static class StageVideoMetadata {
        private String ID;
        private String videoURL;
        private String cityName;
        private String supplierName;
        private String driverName;
        private String fileCityName;
        private boolean cityIllegal;
    }
}

