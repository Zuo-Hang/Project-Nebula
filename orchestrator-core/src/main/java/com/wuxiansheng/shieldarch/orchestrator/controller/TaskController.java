package com.wuxiansheng.shieldarch.orchestrator.controller;

import com.wuxiansheng.shieldarch.orchestrator.dto.TaskSubmitRequest;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskStatusResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskListResponse;
import com.wuxiansheng.shieldarch.orchestrator.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理REST API
 * 
 * 提供任务的提交、查询、详情查看等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    /**
     * 提交任务
     */
    @PostMapping
    public ResponseEntity<TaskStatusResponse> submitTask(@RequestBody TaskSubmitRequest request) {
        log.info("收到任务提交请求: {}", request);
        try {
            TaskStatusResponse response = taskService.submitTask(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("提交任务失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查询任务列表
     */
    @GetMapping
    public ResponseEntity<TaskListResponse> getTaskList(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType) {
        log.debug("查询任务列表: page={}, pageSize={}, status={}, taskType={}", page, pageSize, status, taskType);
        try {
            TaskListResponse response = taskService.getTaskList(page, pageSize, status, taskType);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询任务列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskDetail(@PathVariable String taskId) {
        log.debug("查询任务详情: taskId={}", taskId);
        try {
            TaskStatusResponse response = taskService.getTaskDetail(taskId);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询任务详情失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancelTask(@PathVariable String taskId) {
        log.info("取消任务: taskId={}", taskId);
        try {
            taskService.cancelTask(taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("取消任务失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

