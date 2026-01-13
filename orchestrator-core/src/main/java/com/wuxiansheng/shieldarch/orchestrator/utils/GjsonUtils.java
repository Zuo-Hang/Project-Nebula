package com.wuxiansheng.shieldarch.orchestrator.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GJSON工具类
 * 
 */
@Slf4j
@Component
public class GjsonUtils {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * GjsonExtractStringSlice - 从JSON中提取字符串数组
     * 
     * @param jsonStr JSON字符串
     * @param path JSON路径（如 "data.data86.#.key"）
     * @return 字符串列表
     */
    public List<String> gjsonExtractStringSlice(String jsonStr, String path) {
        List<String> result = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode node = root;
            
            // 解析路径（简化版，只支持简单的路径）
            String[] parts = path.split("\\.");
            
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                
                if ("#".equals(part)) {
                    // 数组遍历
                    if (node.isArray()) {
                        String nextField = i + 1 < parts.length ? parts[i + 1] : null;
                        for (JsonNode item : node) {
                            if (nextField != null && item.has(nextField)) {
                                JsonNode fieldNode = item.get(nextField);
                                if (fieldNode.isTextual()) {
                                    result.add(fieldNode.asText());
                                } else if (fieldNode.isValueNode()) {
                                    result.add(fieldNode.asText());
                                }
                            }
                        }
                        return result;
                    }
                } else {
                    if (node.has(part)) {
                        node = node.get(part);
                    } else {
                        return result; // 路径不存在，返回空列表
                    }
                }
            }
            
            // 如果最后是一个值节点，添加到结果
            if (node.isTextual()) {
                result.add(node.asText());
            } else if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isTextual()) {
                        result.add(item.asText());
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("解析JSON路径失败: path={}, error={}", path, e.getMessage());
        }
        
        return result;
    }
}

