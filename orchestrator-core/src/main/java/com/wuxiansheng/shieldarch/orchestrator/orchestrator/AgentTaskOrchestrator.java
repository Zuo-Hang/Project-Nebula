package com.wuxiansheng.shieldarch.orchestrator.orchestrator;

import com.wuxiansheng.shieldarch.governance.handler.SelfCorrectionHandler;
import com.wuxiansheng.shieldarch.governance.validator.DualCheckValidator;
import com.wuxiansheng.shieldarch.orchestrator.monitor.MetricsClientAdapter;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateMachine;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.TaskStateStore;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptManager;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.PromptCanaryManager;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepExecutor;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepRequest;
import com.wuxiansheng.shieldarch.orchestrator.orchestrator.step.StepResult;
import com.wuxiansheng.shieldarch.orchestrator.service.ResultStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * 智能体任务编排器
 * 对应旧项目的 PipelineRunner 和 Scheduler
 * 
 * 核心调度器，负责：
 * - 任务状态机管理
 * - 异步调度StepExecutor
 * - 背压控制（Semaphore）
 * - 任务持久化（Redis）
 */
@Slf4j
@Component
public class AgentTaskOrchestrator {

    @Autowired(required = false)
    private MetricsClientAdapter metricsClient;

    @Autowired
    private List<StepExecutor> stepExecutors;
    
    @Autowired(required = false)
    private DualCheckValidator dualCheckValidator;
    
    @Autowired(required = false)
    private SelfCorrectionHandler selfCorrectionHandler;
    
    @Autowired(required = false)
    private ResultStorageService resultStorageService;
    
    @Autowired(required = false)
    private PromptManager promptManager;
    
    @Autowired(required = false)
    private PromptCanaryManager promptCanaryManager;

    /**
     * 全局背压控制信号量（限制发往下游推理服务的并发数）
     */
    private final Semaphore semaphore;

    /**
     * 任务状态存储（Redis）
     */
    @Autowired(required = false)
    private TaskStateStore stateStore;

    /**
     * 步骤执行顺序配置（DAG）
     */
    private final List<String> stepOrder = new ArrayList<>();

    public AgentTaskOrchestrator() {
        // 默认最大并发数（可从配置读取）
        int maxConcurrent = 50;
        this.semaphore = new Semaphore(maxConcurrent);
        
        // 默认步骤顺序
        stepOrder.add("FrameExtract");
        stepOrder.add("Inference");
    }

    /**
     * 执行任务
     * 
     * @param taskId 任务ID
     * @param context 任务上下文
     * @return CompletableFuture，任务完成时返回
     */
    public CompletableFuture<Void> executeTask(String taskId, TaskContext context) {
        return CompletableFuture.runAsync(() -> {
            Instant startTime = Instant.now();
            
            try {
                // 1. 初始化任务状态机
                TaskStateMachine stateMachine = TaskStateMachine.create(taskId, context);
                stateMachine.markRunning();
                
                // 2. 持久化任务状态（保存到Redis）
                saveTaskState(stateMachine);
                
                // 3. 按顺序执行步骤
                for (String stepName : stepOrder) {
                    StepExecutor executor = findStepExecutor(stepName);
                    if (executor == null) {
                        log.warn("步骤执行器未找到: {}", stepName);
                        continue;
                    }
                    
                    // 检查是否已执行（支持断点续传）
                    if (stateMachine.isStepExecuted(stepName)) {
                        log.info("步骤已执行，跳过: taskId={}, step={}", taskId, stepName);
                        continue;
                    }
                    
                    // 执行步骤（受背压控制）
                    StepResult stepResult = executeStepWithBackpressure(stateMachine, executor, context);
                    
                    // 如果是推理步骤，执行质量治理（校验 + 自愈）
                    if ("Inference".equals(stepName) && stepResult != null && stepResult.getContent() != null) {
                        performQualityGovernance(stateMachine, context, stepResult, executor);
                    }
                    
                    // 标记步骤已执行
                    stateMachine.markStepExecuted(stepName);
                    
                    // 持久化任务状态
                    saveTaskState(stateMachine);
                }
                
                // 4. 标记任务完成
                stateMachine.markCompleted();
                saveTaskState(stateMachine);
                
                // 5. 保存结果到持久化存储
                if (resultStorageService != null) {
                    resultStorageService.saveResult(stateMachine, context);
                }
                
                // 6. 上报指标
                Duration duration = Duration.between(startTime, Instant.now());
                reportTaskCompletion(taskId, duration, true);
                
                log.info("任务执行完成: taskId={}, duration={}ms", taskId, duration.toMillis());
                
            } catch (Exception e) {
                log.error("任务执行失败: taskId={}", taskId, e);
                
                // 更新任务状态为失败
                TaskStateMachine stateMachine = loadTaskState(taskId);
                if (stateMachine != null) {
                    stateMachine.markFailed(e.getMessage());
                    saveTaskState(stateMachine);
                }
                
                // 上报指标
                Duration duration = Duration.between(startTime, Instant.now());
                reportTaskCompletion(taskId, duration, false);
                
                throw new RuntimeException("任务执行失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 执行步骤（受背压控制）
     * 
     * @return 步骤执行结果
     */
    private StepResult executeStepWithBackpressure(TaskStateMachine stateMachine, 
                                             StepExecutor executor, 
                                             TaskContext context) throws Exception {
        String stepName = executor.getName();
        String taskId = stateMachine.getTaskId();
        
        log.info("开始执行步骤: taskId={}, step={}", taskId, stepName);
        
        Instant stepStart = Instant.now();
        
        try {
            // 申请信号量（背压控制）
            semaphore.acquire();
            
            try {
                // 构建步骤请求
                StepRequest request = buildStepRequest(context, stepName);
                
                // 执行步骤
                CompletableFuture<StepResult> future = executor.execute(context, request);
                StepResult result = future.get(); // 等待完成
                
                // 检查结果
                if (result.hasError()) {
                    throw new Exception("步骤执行失败: " + result.getError().getMessage(), result.getError());
                }
                
                // 更新上下文
                updateContextFromResult(context, result);
                
                // 上报指标
                Duration stepDuration = Duration.between(stepStart, Instant.now());
                reportStepExecution(taskId, stepName, stepDuration, true);
                
                log.info("步骤执行完成: taskId={}, step={}, duration={}ms", 
                    taskId, stepName, stepDuration.toMillis());
                
                return result;
                
            } finally {
                // 释放信号量
                semaphore.release();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("步骤执行被中断: " + stepName, e);
        }
    }
    
    /**
     * 执行质量治理（校验 + 自愈）
     */
    private void performQualityGovernance(
            TaskStateMachine stateMachine,
            TaskContext context,
            StepResult stepResult,
            StepExecutor executor) {
        
        if (dualCheckValidator == null) {
            log.debug("DualCheckValidator未配置，跳过质量治理");
            return;
        }
        
        String taskId = stateMachine.getTaskId();
        String content = stepResult.getContent();
        
        log.info("开始质量治理: taskId={}", taskId);
        
        try {
            // 1. 执行双路校验
            DualCheckValidator.ValidationResult validationResult = 
                dualCheckValidator.validate(context, content);
            
            if (validationResult.isValid()) {
                log.info("质量校验通过: taskId={}", taskId);
                return;
            }
            
            log.warn("质量校验失败: taskId={}, errorCount={}", 
                taskId, validationResult.getErrors().size());
            
            // 2. 如果校验失败，执行自愈重试
            if (selfCorrectionHandler != null) {
                log.info("开始自愈重试: taskId={}", taskId);
                
                // 获取原始Prompt和图片信息
                String originalPrompt = context.getString("originalPrompt");
                if (originalPrompt == null || originalPrompt.isEmpty()) {
                    // 如果没有存储原始prompt，重新构建（用于记录）
                    originalPrompt = buildInferencePrompt(context);
                }
                
                String imageUrl = context.getImagePaths() != null && !context.getImagePaths().isEmpty()
                    ? context.getImagePaths().get(0) : null;
                String ocrText = context.getOcrTextByImage() != null && imageUrl != null
                    ? context.getOcrTextByImage().getOrDefault(imageUrl, "") : "";
                
                // 执行自愈重试（使用PromptManager构建反思Prompt）
                SelfCorrectionHandler.CorrectionResult correctionResult = 
                    selfCorrectionHandler.correctAndRetry(
                        context, originalPrompt, content, 
                        validationResult.getErrors(), imageUrl, ocrText, promptManager);
                
                if (correctionResult.isSuccess()) {
                    log.info("自愈重试成功: taskId={}, retryCount={}", 
                        taskId, correctionResult.getRetryCount());
                    
                    // 更新结果内容
                    stepResult.setContent(correctionResult.getCorrectedContent());
                    context.set("llmContent", correctionResult.getCorrectedContent());
                    
                    // 上报指标
                    if (metricsClient != null) {
                        Map<String, String> tags = new HashMap<>();
                        tags.put("task_id", taskId);
                        tags.put("retry_count", String.valueOf(correctionResult.getRetryCount()));
                        metricsClient.incrementCounter("step_retry_count", tags);
                    }
                } else {
                    log.warn("自愈重试失败: taskId={}, retryCount={}", 
                        taskId, correctionResult.getRetryCount());
                }
            } else {
                log.warn("SelfCorrectionHandler未配置，无法执行自愈重试");
            }
            
        } catch (Exception e) {
            log.error("质量治理执行失败: taskId={}, error={}", taskId, e.getMessage(), e);
            // 不抛出异常，允许任务继续执行
        }
    }

    /**
     * 查找步骤执行器
     */
    private StepExecutor findStepExecutor(String stepName) {
        return stepExecutors.stream()
            .filter(executor -> executor.getName().equals(stepName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 构建步骤请求
     */
    private StepRequest buildStepRequest(TaskContext context, String stepName) {
        StepRequest request = new StepRequest();
        request.setContext(new HashMap<>());
        request.setParams(new HashMap<>());
        
        // 根据步骤类型设置不同的参数
        switch (stepName) {
            case "FrameExtract":
                request.setVideoPath(context.getLocalVideoPath());
                request.getParams().put("intervalSeconds", 1.0);
                request.getParams().put("maxFrames", 0);
                request.getParams().put("quality", 2);
                request.getParams().put("uploadToS3", false);
                break;
            case "Inference":
                request.setImageUrl(context.getImagePaths() != null && !context.getImagePaths().isEmpty() 
                    ? context.getImagePaths().get(0) : null);
                
                // 使用PromptManager构建prompt（支持灰度版本）
                String prompt = buildInferencePrompt(context);
                request.setPrompt(prompt);
                
                // 保存原始prompt到上下文（用于后续自愈重试）
                context.set("originalPrompt", prompt);
                break;
        }
        
        return request;
    }

    /**
     * 构建推理Prompt
     */
    private String buildInferencePrompt(TaskContext context) {
        // 1. 优先从上下文获取自定义prompt
        String customPrompt = context.getString("prompt");
        if (customPrompt != null && !customPrompt.isEmpty()) {
            log.debug("使用上下文中的自定义prompt: taskId={}", context.getTaskId());
            return customPrompt;
        }
        
        // 2. 使用PromptManager构建prompt（支持灰度版本）
        if (promptManager != null) {
            try {
                // 获取业务类型（从taskType或linkName推断）
                String bizType = inferBizType(context);
                
                // 构建上下文变量
                Map<String, Object> promptContext = buildPromptContext(context);
                
                // 构建组合Prompt（System + Business），支持灰度版本
                String prompt;
                if (promptCanaryManager != null) {
                    // 使用灰度版本（如果启用）
                    prompt = promptManager.buildPromptWithCanary(
                        bizType, 
                        PromptManager.PromptStage.EXTRACTION, 
                        promptContext, 
                        context.getTaskId()
                    );
                } else {
                    // 使用稳定版本
                    prompt = promptManager.buildCombinedPrompt(bizType, promptContext);
                }
                
                log.debug("使用PromptManager构建prompt: taskId={}, bizType={}, promptLength={}", 
                    context.getTaskId(), bizType, prompt.length());
                
                return prompt;
                
            } catch (Exception e) {
                log.warn("PromptManager构建prompt失败，使用默认prompt: taskId={}, error={}", 
                    context.getTaskId(), e.getMessage());
            }
        }
        
        // 3. 使用默认prompt
        String defaultPrompt = "请根据图片和OCR文本进行推理";
        log.debug("使用默认prompt: taskId={}", context.getTaskId());
        return defaultPrompt;
    }
    
    /**
     * 推断业务类型
     */
    private String inferBizType(TaskContext context) {
        // 1. 从taskType推断
        String taskType = context.getTaskType();
        if (taskType != null && !taskType.isEmpty()) {
            // 转换为业务类型（如：gaode_video -> GAODE）
            String bizType = taskType.toUpperCase();
            if (bizType.contains("GAODE") || bizType.contains("高德")) {
                return "GAODE";
            } else if (bizType.contains("XIAOLA") || bizType.contains("小拉")) {
                return "XIAOLA";
            }
            return bizType;
        }
        
        // 2. 从linkName推断
        String linkName = context.getLinkName();
        if (linkName != null && !linkName.isEmpty()) {
            String lowerLinkName = linkName.toLowerCase();
            if (lowerLinkName.contains("gaode") || lowerLinkName.contains("高德")) {
                return "GAODE";
            } else if (lowerLinkName.contains("xiaola") || lowerLinkName.contains("小拉")) {
                return "XIAOLA";
            }
        }
        
        // 3. 默认业务类型
        return "DEFAULT";
    }
    
    /**
     * 构建Prompt上下文变量
     */
    private Map<String, Object> buildPromptContext(TaskContext context) {
        Map<String, Object> promptContext = new HashMap<>();
        
        // 基础信息
        promptContext.put("taskId", context.getTaskId());
        promptContext.put("taskType", context.getTaskType());
        promptContext.put("linkName", context.getLinkName());
        promptContext.put("submitDate", context.getSubmitDate());
        
        // OCR文本（如果有）
        if (context.getOcrTextByImage() != null && !context.getOcrTextByImage().isEmpty()) {
            // 使用第一张图片的OCR文本
            String firstImageUrl = context.getImagePaths() != null && !context.getImagePaths().isEmpty() 
                ? context.getImagePaths().get(0) : null;
            if (firstImageUrl != null) {
                String ocrText = context.getOcrTextByImage().getOrDefault(firstImageUrl, "");
                promptContext.put("ocrData", ocrText);
            }
        }
        
        // 目标字段（可以从配置或上下文获取）
        String targetField = context.getString("targetField");
        if (targetField == null || targetField.isEmpty()) {
            targetField = "关键信息"; // 默认值
        }
        promptContext.put("targetField", targetField);
        
        // 角色（可以从配置获取）
        String role = context.getString("role");
        if (role == null || role.isEmpty()) {
            role = "数据分析专家"; // 默认值
        }
        promptContext.put("role", role);
        
        // 自定义数据
        if (context.getCustomData() != null) {
            promptContext.putAll(context.getCustomData());
        }
        
        return promptContext;
    }
    
    /**
     * 从结果更新上下文
     */
    private void updateContextFromResult(TaskContext context, StepResult result) {
        if (result.getImagePaths() != null) {
            context.setImagePaths(result.getImagePaths());
        }
        if (result.getOcrTextByImage() != null) {
            context.setOcrTextByImage(result.getOcrTextByImage());
        }
        if (result.getContent() != null) {
            context.set("llmContent", result.getContent());
        }
        if (result.getData() != null) {
            result.getData().forEach(context::set);
        }
    }

    /**
     * 保存任务状态（到Redis）
     */
    private void saveTaskState(TaskStateMachine stateMachine) {
        // TODO: 实现Redis持久化
        if (stateStore != null) {
            stateStore.save(stateMachine);
        }
    }

    /**
     * 加载任务状态（从Redis）
     */
    private TaskStateMachine loadTaskState(String taskId) {
        // TODO: 实现Redis加载
        if (stateStore != null) {
            return stateStore.load(taskId);
        }
        return null;
    }

    /**
     * 上报任务完成指标
     */
    private void reportTaskCompletion(String taskId, Duration duration, boolean success) {
        if (metricsClient != null) {
            Map<String, String> tags = new HashMap<>();
            tags.put("task_id", taskId);
            tags.put("status", success ? "success" : "failed");
            
            metricsClient.timing("task_completion_time", duration.toMillis(), tags);
            metricsClient.incrementCounter("task_status_total", tags);
        }
    }

    /**
     * 上报步骤执行指标
     */
    private void reportStepExecution(String taskId, String stepName, Duration duration, boolean success) {
        if (metricsClient != null) {
            Map<String, String> tags = new HashMap<>();
            tags.put("task_id", taskId);
            tags.put("step", stepName);
            tags.put("status", success ? "success" : "failed");
            
            metricsClient.timing("step_execution_time", duration.toMillis(), tags);
            metricsClient.incrementCounter("step_execution_total", tags);
        }
    }

}

