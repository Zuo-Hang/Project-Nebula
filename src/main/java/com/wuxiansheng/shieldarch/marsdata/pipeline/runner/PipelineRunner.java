package com.wuxiansheng.shieldarch.marsdata.pipeline.runner;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Pipeline运行器
 * 对应 Go 版本的 runner.PipelineRunner
 */
@Slf4j
public class PipelineRunner {

    private final List<PipelineStage> stages = new ArrayList<>();

    /**
     * 添加阶段
     */
    public void add(PipelineStage stage) {
        if (stage != null) {
            stages.add(stage);
        }
    }

    /**
     * 获取所有阶段
     */
    public List<PipelineStage> getStages() {
        return new ArrayList<>(stages);
    }

    /**
     * 运行Pipeline
     * 
     * @param pipelineCtx Pipeline上下文
     * @throws Exception 处理失败时抛出异常
     */
    public void run(PipelineContext pipelineCtx) throws Exception {
        for (int i = 0; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);
            long startTime = System.currentTimeMillis();

            try {
                log.info("执行阶段 {} ({})", i + 1, stage.name());
                CompletableFuture<Void> future = stage.process(pipelineCtx);
                if (future != null) {
                    future.get(); // 等待完成
                }

                long duration = System.currentTimeMillis() - startTime;
                if (duration > 100) {
                    log.info("步骤 {} ({}) 完成，耗时: {}ms", i + 1, stage.name(), duration);
                }
            } catch (Exception e) {
                log.error("阶段 {} ({}) 执行失败", i + 1, stage.name(), e);
                throw new Exception("阶段 " + stage.name() + " 执行失败: " + e.getMessage(), e);
            }
        }
    }
}

