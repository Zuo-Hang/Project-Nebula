package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import java.util.concurrent.CompletableFuture;

/**
 * Pipeline阶段接口
 * 对应 Go 版本的 interfaces.PipelineStage
 */
public interface PipelineStage {
    /**
     * 返回阶段的名称
     */
    String name();

    /**
     * 返回阶段的功能描述
     */
    String describe();

    /**
     * 处理阶段逻辑
     * 
     * @param pipelineCtx Pipeline上下文
     * @return CompletableFuture，处理完成时返回
     * @throws Exception 处理失败时抛出异常
     */
    CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception;
}

