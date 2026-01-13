package com.wuxiansheng.shieldarch.stepexecutors.executors;

import com.wuxiansheng.shieldarch.orchestrator.config.CityMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 视频元数据提取器
 * 参考旧项目的 VideoMetadataStage 实现
 * 
 * 从视频路径和文件名中提取元数据（城市、供应商、司机等）
 */
@Slf4j
@Component
public class VideoMetadataExtractor {
    
    /**
     * 提取视频元数据
     * 
     * @param videoKey 视频键（S3路径）
     * @param delimiter 字段分隔符（默认"-"）
     * @param tokensOrder 字段顺序（如 ["date", "city_name", "supplier_name", "driver_name"]）
     * @return 视频元数据
     */
    public VideoMetadata extract(String videoKey, String delimiter, String[] tokensOrder) {
        if (videoKey == null || videoKey.isEmpty()) {
            return null;
        }
        
        // 解析路径
        String[] parts = videoKey.split("/");
        if (parts.length < 2) {
            log.warn("视频路径格式不正确: {}", videoKey);
            return null;
        }
        
        String parentCity = parts[parts.length - 2];
        
        // 解析文件名
        String base = new File(videoKey).getName();
        String nameNoExt = base.substring(0, base.lastIndexOf('.') > 0 ? base.lastIndexOf('.') : base.length());
        
        // 标准化文件名
        String cleanedName = normalizeFilename(nameNoExt);
        
        // 使用分隔符分割字段
        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = "-";
        }
        String[] fields = cleanedName.split(Pattern.quote(delimiter));
        
        if (fields.length < 3) {
            log.warn("视频文件名字段不足: {}, fields={}", nameNoExt, fields.length);
            return null;
        }
        
        // 解析字段
        Map<String, String> parsed = parseFields(fields, tokensOrder, nameNoExt);
        
        // 标准化城市名称
        final String citySuffix = "市";
        String stdParentCity = normalizeCityName(parentCity, citySuffix);
        String stdFileCity = normalizeCityName(parsed.get("city_name"), citySuffix);
        
        // 标准化供应商名称
        String stdSupplier = standardizeSupplierName(parsed.get("supplier_name"));
        
        // 检查城市是否匹配
        boolean cityIllegal = !stdParentCity.equals(stdFileCity);
        
        // 构建元数据
        VideoMetadata metadata = new VideoMetadata();
        metadata.setId(videoKey.substring(0, videoKey.lastIndexOf('.') > 0 ? videoKey.lastIndexOf('.') : videoKey.length()));
        metadata.setVideoUrl(videoKey);
        metadata.setCityName(stdParentCity);
        metadata.setSupplierName(stdSupplier);
        metadata.setDriverName(parsed.get("driver_name"));
        metadata.setFileCityName(stdFileCity);
        metadata.setCityIllegal(cityIllegal);
        
        log.info("视频元数据提取完成: {} -> {}/{}/{}", videoKey, stdParentCity, stdSupplier, parsed.get("driver_name"));
        
        return metadata;
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
     * 解析字段
     */
    private Map<String, String> parseFields(String[] fields, String[] tokensOrder, String nameNoExt) {
        Map<String, String> out = new HashMap<>();
        
        // 如果第一个字段是8位数字（日期），则使用固定格式
        if (fields.length >= 4 && isEightDigit(fields[0])) {
            out.put("city_name", fields[1]);
            out.put("supplier_name", fields[2]);
            out.put("driver_name", fields[3]);
            return out;
        }
        
        // 使用配置的字段顺序
        if (tokensOrder != null) {
            for (int i = 0; i < tokensOrder.length && i < fields.length; i++) {
                out.put(tokensOrder[i], fields[i]);
            }
        } else {
            // 默认顺序：从后往前取
            if (fields.length >= 4) {
                out.put("city_name", fields[fields.length - 2]);
                out.put("supplier_name", fields[fields.length - 4]);
                out.put("driver_name", fields[fields.length - 3]);
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
        // 供应商名称映射表
        Map<String, String> supplierMap = new HashMap<>();
        supplierMap.put("星辉", "星徽出行");
        supplierMap.put("星徽", "星徽出行");
        supplierMap.put("曹操", "曹操出行");
        supplierMap.put("T3", "T3出行");
        return supplierMap.getOrDefault(name, name);
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
     * 视频元数据
     */
    @Data
    public static class VideoMetadata {
        /**
         * 视频ID
         */
        private String id;
        
        /**
         * 视频URL
         */
        private String videoUrl;
        
        /**
         * 城市名称（从路径提取）
         */
        private String cityName;
        
        /**
         * 供应商名称
         */
        private String supplierName;
        
        /**
         * 司机名称
         */
        private String driverName;
        
        /**
         * 文件中的城市名称（从文件名提取）
         */
        private String fileCityName;
        
        /**
         * 城市是否不匹配
         */
        private boolean cityIllegal;
    }
}

