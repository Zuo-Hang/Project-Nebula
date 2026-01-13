package com.wuxiansheng.shieldarch.orchestrator.orchestrator;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务状态机
 * 对应旧项目的 PipelineContext（持久化版本）
 * 
 * 用于实现任务的精准一次（Exactly-once）和断点续传
 */
@Data
public class TaskStateMachine {
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 任务上下文快照
     */
    private TaskContext context;

    /**
     * 已执行的步骤列表
     */
    private List<String> executedSteps = new ArrayList<>();

    /**
     * 当前步骤索引
     */
    private int currentStepIndex = 0;

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
     * 错误信息
     */
    private String errorMessage;

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        /**
         * 待执行
         */
        PENDING,

        /**
         * 执行中
         */
        RUNNING,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 失败
         */
        FAILED,

        /**
         * 已取消
         */
        CANCELLED
    }

    /**
     * 创建新的任务状态机
     */
    public static TaskStateMachine create(String taskId, TaskContext context) {
        TaskStateMachine stateMachine = new TaskStateMachine();
        stateMachine.setTaskId(taskId);
        stateMachine.setContext(context);
        stateMachine.setStatus(TaskStatus.PENDING);
        stateMachine.setCreatedAt(Instant.now());
        stateMachine.setUpdatedAt(Instant.now());
        return stateMachine;
    }

    /**
     * 标记步骤已执行
     */
    public void markStepExecuted(String stepName) {
        if (!executedSteps.contains(stepName)) {
            executedSteps.add(stepName);
        }
        currentStepIndex++;
        updatedAt = Instant.now();
    }

    /**
     * 检查步骤是否已执行
     */
    public boolean isStepExecuted(String stepName) {
        return executedSteps.contains(stepName);
    }

    /**
     * 更新状态为运行中
     */
    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    /**
     * 更新状态为已完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 更新状态为失败
     */
    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    /**
     * 更新状态为已取消
     */
    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}

