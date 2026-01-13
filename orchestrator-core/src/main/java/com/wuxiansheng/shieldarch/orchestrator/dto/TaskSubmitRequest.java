package com.wuxiansheng.shieldarch.orchestrator.dto;

import lombok.Data;

import java.util.Map;

/**
 * 任务提交请求DTO
 */
@Data
public class TaskSubmitRequest {
    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 视频Key（S3路径）
     */
    private String videoKey;

    /**
     * 链接名称
     */
    private String linkName;

    /**
     * 提交日期
     */
    private String submitDate;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 视频路径
     */
    private String videoPath;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 自定义数据
     */
    private Map<String, Object> customData;
}

