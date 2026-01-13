package com.wuxiansheng.shieldarch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.orchestrator.io.MysqlWrapper;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 结果存储服务
 * 
 * 负责将任务处理结果写入持久化存储（MySQL/Hive/向量数据库）
 */
@Slf4j
@Service
public class ResultStorageService {
    
    @Autowired(required = false)
    private MysqlWrapper mysqlWrapper;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    /**
     * 是否启用结果存储
     */
    @Value("${orchestrator.result-storage.enabled:true}")
    private boolean enabled;
    
    /**
     * 结果表名
     */
    @Value("${orchestrator.result-storage.table-name:task_results}")
    private String tableName;
    
    public ResultStorageService() {
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
    }
    
    /**
     * 保存任务结果
     * 
     * @param stateMachine 任务状态机
     * @param context 任务上下文
     */
    public void saveResult(TaskStateMachine stateMachine, TaskContext context) {
        if (!enabled) {
            log.debug("结果存储已禁用，跳过保存");
            return;
        }
        
        if (mysqlWrapper == null) {
            log.warn("MysqlWrapper未配置，无法保存结果");
            return;
        }
        
        try {
            String taskId = stateMachine.getTaskId();
            
            log.info("开始保存任务结果: taskId={}", taskId);
            
            // 构建结果数据
            Map<String, Object> resultData = buildResultData(stateMachine, context);
            
            // 保存到MySQL
            saveToMySQL(taskId, resultData);
            
            log.info("任务结果已保存: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("保存任务结果失败: taskId={}, error={}", 
                stateMachine.getTaskId(), e.getMessage(), e);
            // 不抛出异常，允许任务继续完成
        }
    }
    
    /**
     * 构建结果数据
     */
    private Map<String, Object> buildResultData(TaskStateMachine stateMachine, TaskContext context) {
        Map<String, Object> data = new HashMap<>();
        
        // 基本信息
        data.put("task_id", stateMachine.getTaskId());
        data.put("task_type", context.getTaskType());
        data.put("status", stateMachine.getStatus().name());
        data.put("created_at", stateMachine.getCreatedAt());
        data.put("updated_at", stateMachine.getUpdatedAt());
        data.put("completed_at", stateMachine.getCompletedAt());
        
        // 上下文信息
        data.put("video_key", context.getVideoKey());
        data.put("link_name", context.getLinkName());
        data.put("submit_date", context.getSubmitDate());
        
        // 处理结果
        if (context.getImagePaths() != null) {
            data.put("image_count", context.getImagePaths().size());
        }
        
        // LLM推理结果
        String llmContent = context.getString("llmContent");
        if (llmContent != null) {
            data.put("llm_content", llmContent);
        }
        
        // OCR结果摘要
        if (context.getOcrTextByImage() != null) {
            data.put("ocr_text_count", context.getOcrTextByImage().size());
        }
        
        // 自定义数据（JSON序列化）
        if (context.getCustomData() != null && !context.getCustomData().isEmpty()) {
            try {
                String customDataJson = objectMapper.writeValueAsString(context.getCustomData());
                data.put("custom_data", customDataJson);
            } catch (Exception e) {
                log.warn("序列化自定义数据失败: {}", e.getMessage());
            }
        }
        
        return data;
    }
    
    /**
     * 保存到MySQL
     */
    private void saveToMySQL(String taskId, Map<String, Object> resultData) throws Exception {
        // 构建INSERT或UPDATE SQL
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        sql.append("task_id, task_type, status, created_at, updated_at, completed_at, ");
        sql.append("video_key, link_name, submit_date, image_count, llm_content, ocr_text_count, custom_data");
        sql.append(") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ");
        sql.append("ON DUPLICATE KEY UPDATE ");
        sql.append("status = VALUES(status), ");
        sql.append("updated_at = VALUES(updated_at), ");
        sql.append("completed_at = VALUES(completed_at), ");
        sql.append("image_count = VALUES(image_count), ");
        sql.append("llm_content = VALUES(llm_content), ");
        sql.append("ocr_text_count = VALUES(ocr_text_count), ");
        sql.append("custom_data = VALUES(custom_data)");
        
        // 准备参数
        Object[] params = new Object[]{
            resultData.get("task_id"),
            resultData.get("task_type"),
            resultData.get("status"),
            resultData.get("created_at"),
            resultData.get("updated_at"),
            resultData.get("completed_at"),
            resultData.get("video_key"),
            resultData.get("link_name"),
            resultData.get("submit_date"),
            resultData.get("image_count"),
            resultData.get("llm_content"),
            resultData.get("ocr_text_count"),
            resultData.get("custom_data")
        };
        
        // 执行SQL
        mysqlWrapper.executeUpdate(sql.toString(), params);
        
        log.debug("结果已保存到MySQL: taskId={}, table={}", taskId, tableName);
    }
    
    /**
     * 创建结果表（如果不存在）
     * 
     * 这个方法可以在应用启动时调用，确保表存在
     */
    public void createTableIfNotExists() {
        if (mysqlWrapper == null) {
            return;
        }
        
        try {
            String createTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                "task_id VARCHAR(255) PRIMARY KEY, " +
                "task_type VARCHAR(100), " +
                "status VARCHAR(50), " +
                "created_at TIMESTAMP, " +
                "updated_at TIMESTAMP, " +
                "completed_at TIMESTAMP, " +
                "video_key VARCHAR(500), " +
                "link_name VARCHAR(200), " +
                "submit_date VARCHAR(50), " +
                "image_count INT, " +
                "llm_content TEXT, " +
                "ocr_text_count INT, " +
                "custom_data JSON, " +
                "INDEX idx_task_type (task_type), " +
                "INDEX idx_status (status), " +
                "INDEX idx_created_at (created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                tableName
            );
            
            mysqlWrapper.executeUpdate(createTableSql, new Object[]{});
            log.info("结果表已创建或已存在: {}", tableName);
            
        } catch (Exception e) {
            log.error("创建结果表失败: table={}, error={}", tableName, e.getMessage(), e);
        }
    }
}

