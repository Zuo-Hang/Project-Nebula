package com.wuxiansheng.shieldarch.stepexecutors.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * OCR客户端，提供单次批量识别功能
 */
@Slf4j
@Component
public class OcrClient {
    
    private String endpoint;
    private HttpClient httpClient;
    private int maxConcurrency;
    private int batchSize;
    private int maxRetries;
    private Duration retryDelay;
    private double backoffMultiplier;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    /**
     * 创建OCR客户端
     */
    public static OcrClient newOcrClient(OcrConfig ocrConfig) {
        if (ocrConfig == null) {
            throw new RuntimeException("基础配置未加载，请先加载OCR配置");
        }
        
        Duration timeout = parseDuration(ocrConfig.getTimeout());
        Duration retryDelay = parseDuration(ocrConfig.getRetryDelay());
        
        OcrClient client = new OcrClient();
        client.endpoint = ocrConfig.getEndpoint();
        client.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        client.maxConcurrency = ocrConfig.getMaxConcurrency();
        client.batchSize = ocrConfig.getBatchSize();
        client.maxRetries = ocrConfig.getMaxRetries();
        client.retryDelay = retryDelay;
        client.backoffMultiplier = ocrConfig.getBackoffMultiplier();
        client.objectMapper = new ObjectMapper();
        
        return client;
    }
    
    /**
     * 识别一批文件（单次请求，由调用方控制分批/并发/重试）
     */
    public Map<String, AliResult> recognizeFilesOnce(List<String> paths) throws Exception {
        if (paths == null || paths.isEmpty()) {
            return new HashMap<>();
        }
        
        // 验证文件是否存在并读取
        List<byte[]> images = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            Path filePath = Paths.get(path).normalize();
            
            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                throw new Exception(String.format("图片文件不存在 [%d/%d]: %s", 
                    i + 1, paths.size(), path));
            }
            
            byte[] data;
            try {
                data = Files.readAllBytes(filePath);
            } catch (IOException e) {
                throw new Exception(String.format("读取图片文件失败 [%d/%d]: %s (错误: %s)", 
                    i + 1, paths.size(), path, e.getMessage()), e);
            }
            
            if (data.length == 0) {
                throw new Exception(String.format("图片文件为空 [%d/%d]: %s", 
                    i + 1, paths.size(), path));
            }
            
            images.add(data);
        }
        
        return sendOcrRequest(images, paths);
    }
    
    /**
     * 发送OCR HTTP请求
     */
    private Map<String, AliResult> sendOcrRequest(List<byte[]> images, List<String> paths) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new Exception("OCR endpoint 未配置，请检查配置文件");
        }
        if (images.isEmpty()) {
            return new HashMap<>();
        }
        
        // 计算请求大小
        int totalSize = images.stream().mapToInt(img -> img.length).sum();
        
        // 构建请求体
        OcrRequest reqBody = new OcrRequest();
        List<String> encodedImages = new ArrayList<>();
        for (byte[] img : images) {
            String encoded = Base64.getEncoder().encodeToString(img);
            encodedImages.add(encoded);
        }
        reqBody.setImages(encodedImages);
        
        String jsonData = objectMapper.writeValueAsString(reqBody);
        
        // 创建HTTP请求
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonData))
            .timeout(Duration.ofSeconds(120)) // 默认120秒超时
            .build();
        
        // 发送请求
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new Exception(String.format("发送OCR请求失败（端点: %s，图片数量: %d）: %s", 
                endpoint, images.size(), e.getMessage()), e);
        }
        
        // 检查响应状态
        if (response.statusCode() != 200) {
            throw new Exception(String.format("OCR服务返回错误状态码 %d（端点: %s，图片数量: %d）: %s", 
                response.statusCode(), endpoint, images.size(), response.body()));
        }
        
        // 解析响应
        RawResponse raw;
        try {
            raw = objectMapper.readValue(response.body(), RawResponse.class);
        } catch (Exception e) {
            throw new Exception(String.format("解析OCR响应失败（端点: %s，响应大小: %d 字节，图片数量: %d）: %s", 
                endpoint, response.body().length(), images.size(), e.getMessage()), e);
        }
        
        // 验证响应结果数量
        if (raw.getResults() == null || raw.getResults().size() != paths.size()) {
            throw new Exception(String.format("OCR响应结果数量不匹配（期望: %d，实际: %d，端点: %s）", 
                paths.size(), raw.getResults() != null ? raw.getResults().size() : 0, endpoint));
        }
        
        return convertToAli(paths, raw);
    }
    
    /**
     * 将OCR响应转换为AliResult格式
     */
    private Map<String, AliResult> convertToAli(List<String> keys, RawResponse rr) {
        Map<String, AliResult> out = new HashMap<>();
        int n = Math.min(keys.size(), rr.getResults().size());
        
        for (int i = 0; i < n; i++) {
            List<RawPoint> rawLines = rr.getResults().get(i);
            List<String> ocrData = new ArrayList<>();
            List<AliPoint> ocrLocations = new ArrayList<>();
            
            List<String> texts = new ArrayList<>();
            for (RawPoint p : rawLines) {
                AliPoint ap = convertPoint(p);
                ocrLocations.add(ap);
                if (ap.getText() != null && !ap.getText().trim().isEmpty()) {
                    texts.add(ap.getText());
                }
            }
            
            if (!texts.isEmpty()) {
                ocrData.add(String.join(",", texts));
            }
            
            AliResult result = new AliResult();
            result.setOcrData(ocrData);
            result.setOcrLocations(ocrLocations);
            out.put(keys.get(i), result);
        }
        
        return out;
    }
    
    /**
     * 将rawPoint转换为AliPoint
     */
    private AliPoint convertPoint(RawPoint p) {
        String text = normalizeText(p.getText());
        
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        
        if (p.getTextRegion() != null) {
            for (List<Double> pt : p.getTextRegion()) {
                if (pt != null && pt.size() >= 2) {
                    xs.add(pt.get(0));
                    ys.add(pt.get(1));
                }
            }
        }
        
        Collections.sort(xs);
        Collections.sort(ys);
        
        double x = 0, y = 0, w = 0, h = 0;
        if (xs.size() >= 2 && ys.size() >= 2) {
            // 计算宽度：使用最大的两个x坐标和最小的两个x坐标的平均值
            double ww = 0.5 * (xs.get(xs.size() - 1) + xs.get(xs.size() - 2) - xs.get(0) - xs.get(1));
            // 计算高度：使用最大的两个y坐标和最小的两个y坐标的平均值
            double hh = 0.5 * (ys.get(ys.size() - 1) + ys.get(ys.size() - 2) - ys.get(0) - ys.get(1));
            double xm = 0.5 * (xs.get(0) + xs.get(1));
            double ym = 0.5 * (ys.get(0) + ys.get(1));
            h = ym + hh;
            w = xm + ww;
            x = xm;
            y = ym;
        }
        
        double c = p.getConfidence() != null ? p.getConfidence() : 0.0;
        if (c < 0) {
            c = 0;
        }
        
        AliPoint ap = new AliPoint();
        ap.setX(x);
        ap.setY(y);
        ap.setW(w);
        ap.setH(h);
        ap.setText(text);
        ap.setC(round3(c));
        
        return ap;
    }
    
    /**
     * 将英文括号转换为中文括号
     */
    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        s = s.replace("(", "（");
        s = s.replace(")", "）");
        return s;
    }
    
    /**
     * 保留三位小数
     */
    private double round3(double f) {
        return Math.round(f * 1000.0) / 1000.0;
    }
    
    /**
     * 解析持续时间字符串（如 "2s", "100ms"）
     */
    private static Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return Duration.ofSeconds(120); // 默认120秒
        }
        
        try {
            // 移除空格
            durationStr = durationStr.trim();
            
            // 解析秒
            if (durationStr.endsWith("s")) {
                String numStr = durationStr.substring(0, durationStr.length() - 1);
                double seconds = Double.parseDouble(numStr);
                return Duration.ofMillis((long) (seconds * 1000));
            }
            
            // 解析毫秒
            if (durationStr.endsWith("ms")) {
                String numStr = durationStr.substring(0, durationStr.length() - 2);
                long millis = Long.parseLong(numStr);
                return Duration.ofMillis(millis);
            }
            
            // 默认按秒解析
            double seconds = Double.parseDouble(durationStr);
            return Duration.ofMillis((long) (seconds * 1000));
            
        } catch (Exception e) {
            log.warn("解析持续时间失败: {}, 使用默认值120秒", durationStr);
            return Duration.ofSeconds(120);
        }
    }
    
    // ===================== 内部数据类 =====================
    
    @Data
    private static class OcrRequest {
        private List<String> images;
    }
    
    @Data
    private static class RawPoint {
        private String text;
        private Double confidence;
        
        @JsonProperty("text_region")
        private List<List<Double>> textRegion;
    }
    
    @Data
    private static class RawResponse {
        private List<List<RawPoint>> results;
    }
}

