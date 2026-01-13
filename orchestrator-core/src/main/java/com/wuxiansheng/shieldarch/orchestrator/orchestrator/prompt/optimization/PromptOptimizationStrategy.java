package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.optimization;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.AutoPromptOptimizer.OptimizationRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.AutoPromptOptimizer.OptimizationResult;

/**
 * Prompt优化策略接口
 * 
 * 不同的优化策略实现此接口，提供不同的优化方法
 * 
 * 参考：DSPy优化器设计
 */
public interface PromptOptimizationStrategy {
    
    /**
     * 优化Prompt
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param request 优化请求
     * @return 优化结果
     */
    OptimizationResult optimize(
            String bizType, 
            PromptStage stage, 
            OptimizationRequest request);
    
    /**
     * 获取策略名称
     */
    String getStrategyName();
    
    /**
     * 获取策略描述
     */
    String getStrategyDescription();
}
