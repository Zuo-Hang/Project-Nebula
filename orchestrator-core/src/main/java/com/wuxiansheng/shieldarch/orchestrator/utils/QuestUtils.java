package com.wuxiansheng.shieldarch.orchestrator.utils;

import com.wuxiansheng.shieldarch.orchestrator.config.CityMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 问卷工具类
 */
@Slf4j
@Component
public class QuestUtils {
    
    /**
     * QuestImageUrlsV2 - 将路径转换为完整的图片URL
     */
    public List<String> questImageUrlsV2(List<String> paths) {
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            String url = "https://s3-gzpu-inter.didistatic.com/xiaojuwenjuan" + path + "?x-s3-process=image/resize";
            result.add(url);
        }
        return result;
    }
    
    /**
     * QuestImageUrls - 将逗号分隔的URL字符串转换为URL列表
     */
    public List<String> questImageUrls(String data) {
        List<String> result = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return result;
        }
        
        String[] urls = data.split(",");
        for (String url : urls) {
            if (url == null || url.isEmpty()) {
                continue;
            }
            // 替换域名
            url = url.replace("img-hxy021.didistatic.com", "img-hxy02-inter.didistatic.com");
            
            // 如果以//开头，添加http:
            if (url.startsWith("//")) {
                url = "http:" + url;
            }
            
            // 去除末尾的_,字符
            url = url.replaceAll("[_,]+$", "");
            
            if (!url.isEmpty()) {
                result.add(url);
            }
        }
        
        return result;
    }
    
    /**
     * QuestTrim - 清理HTML标签
     */
    public String questTrim(String val) {
        if (val == null) {
            return "";
        }
        String result = val.replace("&lt;p&gt;", "");
        result = result.replace("&lt;/p&gt;", "");
        result = result.replace("&lt;br&gt;", "");
        return result;
    }
    
    /**
     * QuestFormatCityName - 对城市名进行标准化处理
     */
    public String questFormatCityName(String provinceAndCityName) {
        if (provinceAndCityName == null || provinceAndCityName.isEmpty()) {
            return "";
        }
        
        String[] parts = provinceAndCityName.split("-");
        if (parts.length == 0) {
            return "";
        }
        
        String city = parts[parts.length - 1];
        
        // 优先使用标准城市名称
        Map<String, Integer> cityMap = CityMap.getCityMap();
        for (String cityName : cityMap.keySet()) {
            if (city.contains(cityName)) {
                return cityName;
            }
        }
        
        return city;
    }
    
    /**
     * ExtractFloatPrefix - 提取字符串开头的浮点数
     */
    public double extractFloatPrefix(String str) {
        if (str == null || str.isEmpty()) {
            return 0.0;
        }
        
        // 使用正则表达式匹配开头的数字（包括小数点）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^[\\d.]+");
        java.util.regex.Matcher matcher = pattern.matcher(str);
        
        if (matcher.find()) {
            String match = matcher.group();
            try {
                return Double.parseDouble(match);
            } catch (NumberFormatException e) {
                log.debug("解析浮点数失败: {}", match);
            }
        }
        
        return 0.0;
    }
    
    /**
     * ParseMinutesFromString - 从字符串解析分钟数
     * 支持格式：纯数字、"X小时Y分钟"、"X.Y分钟"、"X小时"、"X分钟"
     */
    public double parseMinutesFromString(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 0.0;
        }
        
        // 场景1：兼容纯数字场景
        try {
            return Double.parseDouble(timeStr);
        } catch (NumberFormatException e) {
            // 继续处理其他格式
        }
        
        // 场景2：匹配"X小时Y分钟"或"X.Y分钟"格式
        // 正则表达式：(\d+)小时\s*(\d*\.?\d+)分钟|(\d+\.?\d*)小时|(\d*\.?\d+)分钟
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\d+)小时\\s*(\\d*\\.?\\d+)分钟|(\\d+\\.?\\d*)小时|(\\d*\\.?\\d+)分钟");
        java.util.regex.Matcher matcher = pattern.matcher(timeStr);
        
        if (matcher.find()) {
            double total = 0.0;
            
            // 匹配到"X小时Y分钟"格式
            if (matcher.group(1) != null) {
                try {
                    double hours = Double.parseDouble(matcher.group(1));
                    total += hours * 60;
                } catch (NumberFormatException e) {
                    // 忽略
                }
                if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                    try {
                        double minutes = Double.parseDouble(matcher.group(2));
                        total += minutes;
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
                return total;
            }
            
            // 匹配到"X小时"格式
            if (matcher.group(3) != null) {
                try {
                    double hours = Double.parseDouble(matcher.group(3));
                    return hours * 60;
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            
            // 匹配到"X分钟"格式
            if (matcher.group(4) != null) {
                try {
                    return Double.parseDouble(matcher.group(4));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
        
        return 0.0;
    }
    
    /**
     * MergeString - 合并字符串（优先使用第一个非空值）
     */
    public String mergeString(String str1, String str2) {
        if (str1 != null && !str1.isEmpty()) {
            return str1;
        }
        return str2 != null ? str2 : "";
    }
    
    /**
     * MergeFloat64 - 合并浮点数（优先使用第一个非零值）
     */
    public double mergeFloat64(double f1, double f2) {
        if (f1 != 0.0) {
            return f1;
        }
        return f2;
    }
    
    /**
     * MsToDatetime - 毫秒时间戳转日期时间字符串
     */
    public String msToDatetime(long ms) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(ms);
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * DateTimeToTimestamp - 日期时间字符串转时间戳（秒）
     */
    public long dateTimeToTimestamp(String datetime) throws Exception {
        if (datetime == null || datetime.isEmpty()) {
            throw new Exception("datetime is empty");
        }
        
        java.time.format.DateTimeFormatter formatter = 
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(datetime, formatter);
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }
}

