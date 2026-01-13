package com.wuxiansheng.shieldarch.orchestrator.controller;

import com.wuxiansheng.shieldarch.orchestrator.dto.TaskSubmitRequest;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskStatusResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskListResponse;
import com.wuxiansheng.shieldarch.orchestrator.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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
    
    /**
     * 上传视频文件
     * 
     * 流程：
     * 1. 接收前端上传的文件
     * 2. 调用Service层处理：上传到S3 + 发送MQ消息
     * 3. 返回任务状态
     * 
     * @param file 视频文件
     * @param linkName 链接名称（可选）
     * @param submitDate 提交日期（可选）
     * @return 任务状态响应
     */
    @PostMapping("/upload")
    public ResponseEntity<TaskStatusResponse> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String linkName,
            @RequestParam(required = false) String submitDate,
            @RequestParam(required = false) Map<String, String> customData) {
        
        log.info("收到视频上传请求: filename={}, size={}, linkName={}, submitDate={}", 
            file.getOriginalFilename(), file.getSize(), linkName, submitDate);
        
        try {
            // 验证文件
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // 验证文件类型（可选）
            String contentType = file.getContentType();
            if (contentType != null && !contentType.startsWith("video/")) {
                log.warn("文件类型不正确: contentType={}", contentType);
                // 这里可以选择是否严格验证，暂时允许所有类型
            }
            
            // 调用Service层处理上传和MQ发送
            TaskStatusResponse response = taskService.uploadVideo(file, linkName, submitDate, customData);
            
            log.info("视频上传成功: taskId={}, videoKey={}", response.getTaskId(), response.getVideoKey());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("视频上传参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("视频上传服务未配置: {}", e.getMessage());
            return ResponseEntity.status(503).build(); // Service Unavailable
        } catch (Exception e) {
            log.error("视频上传失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

