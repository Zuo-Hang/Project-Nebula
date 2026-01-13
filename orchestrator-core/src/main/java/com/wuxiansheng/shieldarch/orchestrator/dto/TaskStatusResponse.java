package com.wuxiansheng.shieldarch.orchestrator.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 任务状态响应DTO
 */
@Data
public class TaskStatusResponse {
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * 进度（0-100）
     */
    private Integer progress;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 任务结果
     */
    private Object result;
    
    /**
     * 视频Key（S3路径）
     */
    private String videoKey;
}

