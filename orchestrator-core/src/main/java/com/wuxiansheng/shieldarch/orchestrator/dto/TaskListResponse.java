package com.wuxiansheng.shieldarch.orchestrator.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务列表响应DTO
 */
@Data
public class TaskListResponse {
    /**
     * 任务列表
     */
    private List<TaskStatusResponse> tasks;

    /**
     * 总数
     */
    private Long total;

    /**
     * 当前页
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer pageSize;
}

