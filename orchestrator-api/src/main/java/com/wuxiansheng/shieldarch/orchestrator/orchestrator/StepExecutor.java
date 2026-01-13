package com.wuxiansheng.shieldarch.orchestrator.orchestrator.step;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskContext;

import java.util.concurrent.CompletableFuture;

/**
 * 步骤执行器接口
 * 对应旧项目的 Task 和 PipelineStage
 * 
 * 每个具体的步骤（如抽帧、检测、推理）都是一个实现此接口的Bean
 */
public interface StepExecutor {
    /**
     * 返回步骤名称
     */
    String getName();

    /**
     * 返回步骤的功能描述
     */
    String getDescription();

    /**
     * 执行步骤逻辑
     * 
     * @param context 任务上下文
     * @param request 步骤请求
     * @return CompletableFuture，处理完成时返回步骤结果
     * @throws Exception 处理失败时抛出异常
     */
    CompletableFuture<StepResult> execute(TaskContext context, StepRequest request) throws Exception;
}

