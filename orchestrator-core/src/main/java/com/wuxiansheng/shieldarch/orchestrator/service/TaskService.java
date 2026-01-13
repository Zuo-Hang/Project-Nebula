package com.wuxiansheng.shieldarch.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.orchestrator.bootstrap.MQProducer;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskListResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskStatusResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskSubmitRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.AgentTaskOrchestrator;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateMachine;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateStore;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.stepexecutors.io.S3Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 任务服务
 * 
 * 提供任务的提交、查询等业务逻辑
 */
@Slf4j
@Service
public class TaskService {

    @Autowired
    private AgentTaskOrchestrator orchestrator;

    @Autowired(required = false)
    private TaskStateStore stateStore;
    
    @Autowired(required = false)
    private S3Client s3Client;
    
    @Autowired(required = false)
    private MQProducer mqProducer;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;
    
    /**
     * S3存储桶名称
     */
    @Value("${orchestrator.upload.s3-bucket:ai-orchestrator}")
    private String s3Bucket;
    
    /**
     * S3存储路径前缀
     */
    @Value("${orchestrator.upload.s3-prefix:uploads/videos/}")
    private String s3Prefix;
    
    /**
     * MQ Topic（用于上传视频后发送消息）
     */
    @Value("${orchestrator.upload.mq-topic:ocr_video_capture}")
    private String mqTopic;
    
    /**
     * 临时文件存储目录
     */
    @Value("${orchestrator.upload.temp-dir:${java.io.tmpdir}/video_uploads}")
    private String tempDir;

    /**
     * 提交任务
     */
    public TaskStatusResponse submitTask(TaskSubmitRequest request) {
        // 生成任务ID
        String taskId = UUID.randomUUID().toString().replace("-", "");

        // 构建任务上下文
        TaskContext context = new TaskContext();
        context.setTaskId(taskId);
        context.setTaskType(request.getTaskType());
        context.setVideoKey(request.getVideoKey());
        context.setLinkName(request.getLinkName());
        context.setSubmitDate(request.getSubmitDate());
        context.setLocalVideoPath(request.getVideoPath());
        if (request.getImageUrl() != null) {
            context.setImagePaths(List.of(request.getImageUrl()));
        }
        if (request.getPrompt() != null) {
            context.set("prompt", request.getPrompt());
        }
        if (request.getCustomData() != null) {
            request.getCustomData().forEach(context::set);
        }

        // 提交给编排器执行（异步）
        orchestrator.executeTask(taskId, context);

        // 返回任务状态
        return buildTaskStatusResponse(taskId, context);
    }

    /**
     * 查询任务详情
     */
    public TaskStatusResponse getTaskDetail(String taskId) {
        if (stateStore == null) {
            log.warn("TaskStateStore未配置，无法查询任务详情: taskId={}", taskId);
            return null;
        }

        TaskStateMachine stateMachine = stateStore.load(taskId);
        if (stateMachine == null) {
            log.debug("任务不存在: taskId={}", taskId);
            return null;
        }

        return convertToTaskStatusResponse(stateMachine);
    }

    /**
     * 查询任务列表
     * 
     * 注意：这是一个简化实现，实际应该从数据库或Redis中查询
     * 当前实现仅从Redis中查询所有任务（效率较低，仅用于演示）
     */
    public TaskListResponse getTaskList(int page, int pageSize, String status, String taskType) {
        // TODO: 实际应该从数据库查询，这里简化处理
        // 当前实现：返回空列表（因为无法遍历Redis中的所有key）
        // 建议：在MySQL中维护任务列表，或使用Redis的SCAN命令
        
        List<TaskStatusResponse> tasks = new ArrayList<>();
        // 这里应该从数据库查询，暂时返回空列表
        
        TaskListResponse response = new TaskListResponse();
        response.setTasks(tasks);
        response.setTotal(0L);
        response.setPage(page);
        response.setPageSize(pageSize);
        
        return response;
    }

    /**
     * 取消任务
     */
    public void cancelTask(String taskId) {
        // TODO: 实现任务取消逻辑
        log.warn("任务取消功能暂未实现: taskId={}", taskId);
    }
    
    /**
     * 上传视频文件
     * 
     * 流程：
     * 1. 保存文件到临时目录
     * 2. 上传到S3存储
     * 3. 发送MQ消息
     * 4. 清理临时文件
     * 
     * @param file 上传的视频文件
     * @param linkName 链接名称（可选）
     * @param submitDate 提交日期（可选）
     * @param customData 自定义数据（可选）
     * @return 任务状态响应
     * @throws Exception 上传失败时抛出异常
     */
    public TaskStatusResponse uploadVideo(
            MultipartFile file,
            String linkName,
            String submitDate,
            Map<String, String> customData) throws Exception {
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        
        if (s3Client == null) {
            throw new IllegalStateException("S3客户端未配置，无法上传文件");
        }
        
        if (mqProducer == null) {
            throw new IllegalStateException("MQ生产者未配置，无法发送消息");
        }
        
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + fileExtension;
        
        // 构建S3对象键（路径）
        String s3ObjectKey = s3Prefix + uniqueFileName;
        
        // 构建S3视频Key（用于后续处理）
        String videoKey = s3Bucket + "/" + s3ObjectKey;
        
        // 生成任务ID
        String taskId = UUID.randomUUID().toString().replace("-", "");
        
        File tempFile = null;
        Instant uploadStartTime = Instant.now();
        long fileSize = file.getSize();
        
        try {
            // 1. 保存文件到临时目录
            log.info("开始上传视频: taskId={}, originalFilename={}, size={}", 
                taskId, originalFilename, fileSize);
            
            Path tempDirPath = Paths.get(tempDir);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
            
            tempFile = new File(tempDir, uniqueFileName);
            file.transferTo(tempFile);
            log.info("文件已保存到临时目录: {}", tempFile.getAbsolutePath());
            
            // 2. 上传到S3存储（指标由S3Client内部上报）
            log.info("开始上传到S3: bucket={}, objectKey={}", s3Bucket, s3ObjectKey);
            s3Client.uploadFile(s3Bucket, tempFile.getAbsolutePath(), s3ObjectKey);
            log.info("文件已上传到S3: videoKey={}", videoKey);
            
            // 3. 发送MQ消息（指标由MQProducer内部上报）
            log.info("开始发送MQ消息: topic={}, videoKey={}", mqTopic, videoKey);
            String mqMessage = buildMQMessage(videoKey, linkName, submitDate, customData, taskId);
            boolean sent = mqProducer.send(mqTopic, mqMessage);
            
            if (!sent) {
                log.error("MQ消息发送失败: topic={}, videoKey={}", mqTopic, videoKey);
                throw new Exception("MQ消息发送失败");
            }
            log.info("MQ消息发送成功: topic={}, videoKey={}", mqTopic, videoKey);
            
            // 4. 构建任务上下文
            TaskContext context = new TaskContext();
            context.setTaskId(taskId);
            context.setTaskType("video_upload");
            context.setVideoKey(videoKey);
            context.setLinkName(linkName);
            context.setSubmitDate(submitDate);
            if (customData != null) {
                customData.forEach(context::set);
            }
            
            // 5. 计算总耗时并上报成功指标
            long totalDuration = java.time.Duration.between(uploadStartTime, Instant.now()).toMillis();
            
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("task_id", taskId);
                tags.put("status", "success");
                tags.put("bucket", s3Bucket);
                
                // 上报总耗时
                metricsClient.timing("video_upload_duration", totalDuration, tags);
                
                // 上报成功计数
                metricsClient.incrementCounter("video_upload_total", tags);
                
                // 上报文件大小（Gauge）
                tags.put("file_size_range", getFileSizeRange(fileSize));
                metricsClient.recordGauge("video_upload_file_size", fileSize, tags);
            }
            
            // 6. 返回任务状态
            TaskStatusResponse response = buildTaskStatusResponse(taskId, context);
            response.setVideoKey(videoKey);
            log.info("视频上传完成: taskId={}, videoKey={}, totalDuration={}ms, fileSize={}bytes", 
                taskId, videoKey, totalDuration, fileSize);
            
            return response;
            
        } catch (Exception e) {
            // 上报失败指标
            long totalDuration = java.time.Duration.between(uploadStartTime, Instant.now()).toMillis();
            
            if (metricsClient != null) {
                Map<String, String> tags = new HashMap<>();
                tags.put("task_id", taskId);
                tags.put("status", "failed");
                tags.put("error_type", e.getClass().getSimpleName());
                tags.put("bucket", s3Bucket);
                
                // 上报失败耗时
                metricsClient.timing("video_upload_duration", totalDuration, tags);
                
                // 上报失败计数
                metricsClient.incrementCounter("video_upload_total", tags);
            }
            
            log.error("视频上传失败: taskId={}, error={}, duration={}ms", taskId, e.getMessage(), totalDuration, e);
            throw new Exception("视频上传失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                    log.debug("临时文件已删除: {}", tempFile.getAbsolutePath());
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }
    
    /**
     * 获取文件大小范围标签（用于指标分组）
     */
    private String getFileSizeRange(long fileSize) {
        if (fileSize < 1024 * 1024) { // < 1MB
            return "0-1MB";
        } else if (fileSize < 10 * 1024 * 1024) { // 1MB - 10MB
            return "1-10MB";
        } else if (fileSize < 100 * 1024 * 1024) { // 10MB - 100MB
            return "10-100MB";
        } else if (fileSize < 500 * 1024 * 1024) { // 100MB - 500MB
            return "100-500MB";
        } else { // >= 500MB
            return "500MB+";
        }
    }
    
    /**
     * 构建MQ消息
     */
    private String buildMQMessage(
            String videoKey,
            String linkName,
            String submitDate,
            Map<String, String> customData,
            String taskId) throws Exception {
        
        Map<String, Object> message = new HashMap<>();
        message.put("videoKey", videoKey);
        message.put("taskId", taskId);
        message.put("timestamp", System.currentTimeMillis());
        
        if (linkName != null && !linkName.isEmpty()) {
            message.put("linkName", linkName);
        }
        
        if (submitDate != null && !submitDate.isEmpty()) {
            message.put("submitDate", submitDate);
        }
        
        if (customData != null && !customData.isEmpty()) {
            message.put("customData", customData);
        }
        
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        
        return objectMapper.writeValueAsString(message);
    }

    /**
     * 构建任务状态响应
     */
    private TaskStatusResponse buildTaskStatusResponse(String taskId, TaskContext context) {
        TaskStatusResponse response = new TaskStatusResponse();
        response.setTaskId(taskId);
        response.setStatus("PENDING");
        response.setTaskType(context.getTaskType());
        response.setCreatedAt(Instant.now());
        response.setUpdatedAt(Instant.now());
        return response;
    }

    /**
     * 转换TaskStateMachine为TaskStatusResponse
     */
    private TaskStatusResponse convertToTaskStatusResponse(TaskStateMachine stateMachine) {
        TaskStatusResponse response = new TaskStatusResponse();
        response.setTaskId(stateMachine.getTaskId());
        response.setStatus(stateMachine.getStatus().name());
        response.setTaskType(stateMachine.getContext() != null ? stateMachine.getContext().getTaskType() : null);
        response.setCreatedAt(stateMachine.getCreatedAt());
        response.setUpdatedAt(stateMachine.getUpdatedAt());
        response.setCompletedAt(stateMachine.getCompletedAt());
        response.setErrorMessage(stateMachine.getErrorMessage());
        
        // 计算进度（基于已执行的步骤）
        if (stateMachine.getExecutedSteps() != null && !stateMachine.getExecutedSteps().isEmpty()) {
            // 假设总共有2个步骤（FrameExtract, Inference）
            int totalSteps = 2;
            int executedSteps = stateMachine.getExecutedSteps().size();
            response.setProgress((int) (executedSteps * 100.0 / totalSteps));
        } else {
            response.setProgress(0);
        }
        
        // 设置结果（从context中提取）
        if (stateMachine.getContext() != null) {
            // 可以从context中提取结果数据
            response.setResult(stateMachine.getContext().get("result"));
        }
        
        return response;
    }
}

