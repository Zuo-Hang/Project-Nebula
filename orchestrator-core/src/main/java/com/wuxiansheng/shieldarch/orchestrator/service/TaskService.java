package com.wuxiansheng.shieldarch.orchestrator.service;

import com.wuxiansheng.shieldarch.orchestrator.dto.TaskListResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskStatusResponse;
import com.wuxiansheng.shieldarch.orchestrator.dto.TaskSubmitRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.AgentTaskOrchestrator;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateMachine;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateStore;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

