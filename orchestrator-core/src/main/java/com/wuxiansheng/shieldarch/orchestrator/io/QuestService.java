package com.wuxiansheng.shieldarch.orchestrator.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Quest服务
 */
@Slf4j
@Service
public class QuestService {
    
    /**
     * 小桔问卷服务名（支持服务发现格式，如 "disf!service-name" 或直接 IP:Port）
     */
    @Value("${quest.xiaoju-survey.service-name:10.88.128.40:8000}")
    private String xiaoJuSurveyServiceName;
    
    /**
     * 小桔问卷Token
     */
    @Value("${quest.xiaoju-survey.token:eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhcHBpZCI6ImU4ODk0NWRhNGM4ZjhlYTc5ZmI0ZDk0NmVlYWUxYWM5IiwiaWF0IjoxNzU2ODA1OTUxfQ.RzYiC3eDaNbV-TR59M55hDAT1Mo5C3dkeBNlU5UPIX4}")
    private String xiaoJuSurveyToken;
    
    // TODO: 迁移工具类后取消注释
    // @Autowired(required = false)
    // private HttpUtils httpUtils;
    
    // TODO: 迁移工具类后取消注释
    // @Autowired(required = false)
    // private ServiceDiscovery serviceDiscovery;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    // TODO: 迁移监控类后取消注释
    // @Autowired(required = false)
    // private MetricsClientAdapter metricsClient;
    
    public QuestService() {
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
    }
    
    /**
     * 根据创建时间查询问卷
     * 
     * @param activityName 活动名称
     * @param from 开始时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param to 结束时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 问卷消息列表（JSON字符串）
     * @throws Exception 查询失败时抛出异常
     */
    public List<String> queryQuestByCreateAt(String activityName, String from, String to) throws Exception {
        long begin = System.currentTimeMillis();
        Exception error = null;
        
        try {
            // TODO: 迁移工具类后取消注释
            // 获取HTTP端点
            // String ipPort = getHttpEndpoint(xiaoJuSurveyServiceName);
            // if (ipPort == null || ipPort.isEmpty()) {
            //     log.warn("no valid endpoint for {}", xiaoJuSurveyServiceName);
            //     throw new Exception("no valid endpoint");
            // }
            String ipPort = xiaoJuSurveyServiceName; // 临时使用配置值
            
            // 构建查询条件
            String condition = String.format(
                "[{\"key\":\"createdAt\",\"value\":[{\"sign\":\"gte\",\"value\":\"%s\"},{\"sign\":\"lt\",\"value\":\"%s\"}],\"relation\":\"range\"}]",
                from, to);
            String encodedCondition = URLEncoder.encode(condition, StandardCharsets.UTF_8);
            
            // 构建URL
            String url = String.format(
                "http://%s/wenjuan-openapi/survey/api/analysis/recycle?activityName=%s&page=1&pageSize=24000&query=%s",
                ipPort, activityName, encodedCondition);
            
            // 设置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("x-api-token", xiaoJuSurveyToken);
            
            // TODO: 迁移工具类后取消注释
            // 发送HTTP GET请求
            // byte[] resp = httpUtils != null ? httpUtils.httpGet(url, headers) : new byte[0];
            byte[] resp = new byte[0]; // 临时占位
            
            // 解析响应
            QueryQuestResponse questResp = objectMapper.readValue(resp, QueryQuestResponse.class);
            
            if (questResp.getErrno() != 0) {
                throw new Exception(String.format("Quest服务返回错误: errno=%d, errmsg=%s", 
                    questResp.getErrno(), questResp.getErrmsg()));
            }
            
            // 使用接口查询和mq消息的区别在于，问卷id字段不一致，这里做下修正
            if (questResp.getData() != null && questResp.getData().getContent() != null) {
                for (Map<String, Object> content : questResp.getData().getContent()) {
                    if (content.containsKey("bizId")) {
                        content.put("objId", content.get("bizId"));
                    }
                }
            }
            
            // 另一个不一致：data 字段一个是字符串一个是map
            resetContentData(questResp);
            
            // 打包结果
            List<String> res = new ArrayList<>();
            if (questResp.getData() != null && questResp.getData().getContent() != null) {
                for (Map<String, Object> content : questResp.getData().getContent()) {
                    try {
                        String msg = objectMapper.writeValueAsString(content);
                        res.add(msg);
                    } catch (Exception e) {
                        log.warn("json.Marshal from QueryQuestResponse fail, err: {}, content: {}", 
                            e.getMessage(), content);
                        throw e;
                    }
                }
            }
            
            return res;
            
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            // TODO: 迁移监控类后取消注释
            // 记录RPC指标
            // long duration = System.currentTimeMillis() - begin;
            // if (metricsClient != null) {
            //     int responseCode = error == null ? 0 : 1;
            //     metricsClient.recordRpcMetric("quest_req", "self", "QueryQuestByCreateAt", 
            //         duration, responseCode);
            // }
        }
    }
    
    /**
     * 重置Content的data字段（从字符串转换为Map）
     */
    private void resetContentData(QueryQuestResponse questResp) {
        if (questResp.getData() == null || questResp.getData().getContent() == null) {
            return;
        }
        
        for (Map<String, Object> content : questResp.getData().getContent()) {
            Object dataObj = content.get("data");
            if (!(dataObj instanceof String)) {
                log.warn("invalid content data type, content: {}", content);
                continue;
            }
            
            String dataStr = (String) dataObj;
            try {
                Map<String, Object> dataMap = objectMapper.readValue(dataStr, Map.class);
                content.put("data", dataMap);
            } catch (Exception e) {
                log.warn("json.Unmarshal data field fail, err: {}, dataStr: {}", e.getMessage(), dataStr);
            }
        }
    }
    
    /**
     * 获取HTTP端点
     */
    private String getHttpEndpoint(String serviceName) {
        // TODO: 迁移工具类后取消注释
        // if (serviceDiscovery != null && serviceDiscovery.isAvailable()) {
        //     String endpoint = serviceDiscovery.getHttpEndpoint(serviceName);
        //     if (endpoint != null && !endpoint.isEmpty()) {
        //         return endpoint;
        //     }
        // }
        // 如果没有服务发现，直接返回配置的值（可能是IP:Port格式）
        return serviceName;
    }
    
    /**
     * 查询问卷响应
     */
    @lombok.Data
    public static class QueryQuestResponse {
        private Integer errno;
        private String errmsg;
        private QuestData data;
        
        @lombok.Data
        public static class QuestData {
            private List<Map<String, Object>> content;
        }
    }
}

