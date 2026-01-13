package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.optimization;

import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.AutoPromptOptimizer;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager.PromptStage;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot.ExampleSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bootstrap优化器
 * 
 * 功能：
 * 1. 从成功案例中自动选择Few-shot示例
 * 2. 使用Few-shot示例优化Prompt
 * 3. 迭代优化，逐步提升效果
 * 
 * 参考：DSPy BootstrapFewShot
 */
@Slf4j
@Component
public class BootstrapOptimizer implements PromptOptimizationStrategy {
    
    @Autowired(required = false)
    private ExampleSelector exampleSelector;
    
    /**
     * Few-shot示例数量
     */
    private int fewShotCount = 5;
    
    @Override
    public AutoPromptOptimizer.OptimizationResult optimize(
            String bizType, 
            PromptStage stage, 
            AutoPromptOptimizer.OptimizationRequest request) {
        
        log.info("使用Bootstrap优化器优化Prompt: bizType={}, stage={}", bizType, stage);
        
        // 1. 从成功案例中选择Few-shot示例
        List<ExampleSelector.Example> examples = selectBestExamples(request);
        
        if (examples.isEmpty()) {
            log.warn("未找到合适的Few-shot示例，优化失败");
            return AutoPromptOptimizer.OptimizationResult.failed("未找到合适的Few-shot示例");
        }
        
        // 2. 构建优化后的Prompt（包含Few-shot示例）
        String optimizedPrompt = buildPromptWithExamples(request.getCurrentPrompt(), examples);
        
        log.info("Bootstrap优化完成: bizType={}, stage={}, exampleCount={}", 
            bizType, stage, examples.size());
        
        // 3. 返回优化结果（注意：这里不直接发布，由AutoPromptOptimizer处理）
        return AutoPromptOptimizer.OptimizationResult.success(
            "bootstrap_v1", 
            optimizedPrompt, 
            0.01  // 默认1%灰度
        );
    }
    
    /**
     * 选择最佳示例
     */
    private List<ExampleSelector.Example> selectBestExamples(
            AutoPromptOptimizer.OptimizationRequest request) {
        
        if (exampleSelector == null) {
            log.warn("ExampleSelector未配置，无法选择示例");
            return new ArrayList<>();
        }
        
        // 从失败案例中提取查询文本（用于相似度匹配）
        String query = request.getFailureCases().stream()
            .map(AutoPromptOptimizer.OptimizationRequest.FailureCase::getInput)
            .findFirst()
            .orElse("");
        
        if (query.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 选择最相似的示例
        List<ExampleSelector.Example> examples = exampleSelector.selectExamples(query, fewShotCount);
        
        return examples;
    }
    
    /**
     * 构建包含Few-shot示例的Prompt
     */
    private String buildPromptWithExamples(
            String basePrompt, 
            List<ExampleSelector.Example> examples) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append(basePrompt).append("\n\n");
        prompt.append("参考案例：\n");
        
        for (int i = 0; i < examples.size(); i++) {
            ExampleSelector.Example example = examples.get(i);
            prompt.append(String.format("案例%d：\n", i + 1));
            prompt.append(example.toPromptString()).append("\n\n");
        }
        
        prompt.append("请参考以上案例，确保输出格式和内容质量。");
        
        return prompt.toString();
    }
    
    @Override
    public String getStrategyName() {
        return "Bootstrap";
    }
    
    @Override
    public String getStrategyDescription() {
        return "从成功案例中自动选择Few-shot示例，优化Prompt效果";
    }
    
    /**
     * 设置Few-shot示例数量
     */
    public void setFewShotCount(int count) {
        this.fewShotCount = count;
    }
}
