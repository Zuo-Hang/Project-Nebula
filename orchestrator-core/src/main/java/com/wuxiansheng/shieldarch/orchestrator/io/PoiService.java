package com.wuxiansheng.shieldarch.orchestrator.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * POI服务
 */
@Slf4j
@Service
public class PoiService {
    
    /**
     * MapAPI服务URL
     */
    @Value("${poi.mapapi.url:http://10.74.207.15:8014/mapapi/textsearch}")
    private String mapapiUrl;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    // TODO: 迁移监控类后取消注释
    // @Autowired(required = false)
    // private MetricsClientAdapter metricsClient;
    
    // TODO: 迁移工具类后取消注释
    // @Autowired(required = false)
    // private GjsonUtils gjsonUtils;
    
    private final HttpClient httpClient;
    
    public PoiService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
    }
    
    /**
     * 获取POI坐标
     * 
     * @param cityId 城市ID
     * @param poi POI名称
     * @param caller 调用者标识
     * @return 返回经度和纬度的字符串数组，[0]为经度，[1]为纬度
     * @throws Exception 请求失败时抛出异常
     */
    public String[] getPoiCoordinate(Integer cityId, String poi, String caller) throws Exception {
        long begin = System.currentTimeMillis();
        Exception error = null;
        
        try {
            if (cityId == null || cityId == 0 || poi == null || poi.isEmpty()) {
                return new String[]{"", ""};
            }
            
            // 构建请求体
            Map<String, Object> req = new HashMap<>();
            req.put("acc_key", "HA1UC-TH0WZ-DXT1E-4CLUM-AJD4X-K8ESZ");
            req.put("is_search", "0");
            req.put("app_id", "1");
            req.put("caller_id", "anycar");
            req.put("select_lng", "116.516135796");
            req.put("requester_type", "101");
            req.put("coordinate_type", "gcj02");
            req.put("select_lat", "39.9498022461");
            req.put("platform", "3");
            req.put("app_version", "100.100.100");
            req.put("need_loading", "0");
            req.put("phone", "18810672787");
            req.put("map_type", "tmap");
            req.put("start_index", "0");
            req.put("lang", "zh-CN");
            req.put("product_id", "666");
            req.put("user_loc_lng", "116.516135796");
            req.put("place_type", "2");
            req.put("city_id", cityId);
            req.put("query", poi);
            
            // 发送HTTP请求
            String requestBody = objectMapper.writeValueAsString(req);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mapapiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new Exception(String.format("POI服务返回错误状态码 %d: %s", 
                    response.statusCode(), response.body()));
            }
            
            String responseBody = response.body();
            log.info("request poi body: {}, city_id: {}, poi: {}", responseBody, cityId, poi);
            
            // 解析响应
            JsonNode parser = objectMapper.readTree(responseBody);
            String lng = "";
            String lat = "";
            
            // 使用gjson路径提取：result.0.base_info.lng 和 result.0.base_info.lat
            JsonNode resultArray = parser.path("result");
            if (resultArray.isArray() && resultArray.size() > 0) {
                JsonNode firstResult = resultArray.get(0);
                JsonNode baseInfo = firstResult.path("base_info");
                if (baseInfo.has("lng")) {
                    JsonNode lngNode = baseInfo.get("lng");
                    lng = lngNode.isTextual() ? lngNode.asText() : String.valueOf(lngNode.asDouble());
                }
                if (baseInfo.has("lat")) {
                    JsonNode latNode = baseInfo.get("lat");
                    lat = latNode.isTextual() ? latNode.asText() : String.valueOf(latNode.asDouble());
                }
            }
            
            return new String[]{lng, lat};
            
        } catch (Exception e) {
            error = e;
            log.warn("GetPOICoord err: {}, cityId: {}, poi: {}", e.getMessage(), cityId, poi);
            throw e;
        } finally {
            // TODO: 迁移监控类后取消注释
            // 记录RPC指标
            // long duration = System.currentTimeMillis() - begin;
            // if (metricsClient != null) {
            //     int responseCode = error == null ? 0 : 1;
            //     metricsClient.recordRpcMetric("mapapi_req", caller, "mapapi", duration, responseCode);
            // }
        }
    }
    
    /**
     * 获取POI坐标（返回经度）
     * 
     * @param cityId 城市ID
     * @param poi POI名称
     * @param caller 调用者标识
     * @return 经度字符串
     */
    public String getPoiLongitude(Integer cityId, String poi, String caller) throws Exception {
        String[] coords = getPoiCoordinate(cityId, poi, caller);
        return coords[0];
    }
    
    /**
     * 获取POI坐标（返回纬度）
     * 
     * @param cityId 城市ID
     * @param poi POI名称
     * @param caller 调用者标识
     * @return 纬度字符串
     */
    public String getPoiLatitude(Integer cityId, String poi, String caller) throws Exception {
        String[] coords = getPoiCoordinate(cityId, poi, caller);
        return coords[1];
    }
}

