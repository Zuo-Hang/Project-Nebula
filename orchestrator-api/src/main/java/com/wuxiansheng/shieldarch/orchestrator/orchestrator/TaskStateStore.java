package com.wuxiansheng.shieldarch.orchestrator.orchestrator;

/**
 * 任务状态存储接口
 * 
 * 用于持久化任务状态机到Redis等存储
 */
public interface TaskStateStore {
    /**
     * 保存任务状态
     * 
     * @param stateMachine 任务状态机
     */
    void save(TaskStateMachine stateMachine);

    /**
     * 加载任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态机，如果不存在返回null
     */
    TaskStateMachine load(String taskId);
}

