package com.wuxiansheng.shieldarch.orchestrator.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP工具类
 */
@Slf4j
@Component
public class HttpUtils {
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    private final HttpClient httpClient;
    
    public HttpUtils() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
    }
    
    /**
     * 发送HTTP POST请求
     * 
     * @param url 请求URL
     * @param request 请求体对象（会被序列化为JSON）
     * @param headers 请求头
     * @param printBodyAndResult 是否打印请求体和响应体
     * @return 响应体字节数组
     * @throws Exception 请求失败时抛出异常
     */
    public byte[] sendHttpRequest(String url, Object request, Map<String, String> headers, boolean printBodyAndResult) throws Exception {
        // 将请求体转换为JSON
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("SendHTTPRequest err: {}, request: {}", e.getMessage(), request);
            throw new Exception("序列化请求体失败", e);
        }
        
        if (printBodyAndResult) {
            log.info("SendHTTPRequest body: {}", requestBody);
        }
        
        // 创建HTTP请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30));
        
        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        
        // 如果没有设置Content-Type，默认设置为application/json
        if (headers == null || !headers.containsKey("Content-Type")) {
            requestBuilder.header("Content-Type", "application/json");
        }
        
        HttpRequest httpRequest = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // 发送请求
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("SendHTTPRequest client.Do err: {}, url: {}, req: {}", e.getMessage(), url, requestBody);
            throw new Exception("发送请求失败", e);
        }
        
        // 读取响应
        String body = response.body();
        if (printBodyAndResult) {
            log.info("SendHTTPRequest result: {}", body);
        }
        
        return body != null ? body.getBytes() : new byte[0];
    }
    
    /**
     * 发送HTTP GET请求
     * 
     * @param url 请求URL
     * @param headers 请求头
     * @return 响应体字节数组
     * @throws Exception 请求失败时抛出异常
     */
    public byte[] httpGet(String url, Map<String, String> headers) throws Exception {
        // 创建HTTP请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30));
        
        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        
        HttpRequest httpRequest = requestBuilder
            .GET()
            .build();
        
        // 发送请求
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("HTTPGet client.Do err: {}, url: {}", e.getMessage(), url);
            throw new Exception("发送请求失败", e);
        }
        
        // 读取响应
        String body = response.body();
        return body != null ? body.getBytes() : new byte[0];
    }
}

