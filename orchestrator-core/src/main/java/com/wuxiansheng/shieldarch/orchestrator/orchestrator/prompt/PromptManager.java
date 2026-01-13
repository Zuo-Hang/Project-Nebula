package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.wuxiansheng.shieldarch.orchestrator.config.AppConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt管理器
 * 
 * 核心职责：
 * 1. 业务隔离：不同业务使用不同的Prompt模板
 * 2. AI确定性治理：通过模板管理提高AI输出的确定性
 * 3. 版本可追踪：支持Prompt版本化管理
 * 4. 动态编排：支持System、Business、Feedback三种模板类型
 * 
 * 设计理念：
 * - 将Prompt从代码中解耦，存储在配置中心，支持秒级修改
 * - 使用Handlebars模板引擎，支持条件判断、循环等复杂逻辑
 * - 支持Few-shot动态注入（可扩展RAG）
 */
@Slf4j
@Service
public class PromptManager {
    
    private final Handlebars handlebars = new Handlebars();
    
    @Autowired(required = false)
    private AppConfigService appConfigService;
    
    @Autowired(required = false)
    private PromptCanaryManager promptCanaryManager;
    
    @Autowired(required = false)
    private com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.security.InputValidator inputValidator;
    
    @Autowired(required = false)
    private com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot.ExampleSelector exampleSelector;
    
    /**
     * 配置命名空间
     */
    @Value("${prompt.config.namespace:PROMPT_TEMPLATES}")
    private String configNamespace;
    
    /**
     * 是否启用模板缓存
     */
    @Value("${prompt.cache.enabled:true}")
    private boolean cacheEnabled;
    
    /**
     * 模板缓存（key: bizType_stage_version, value: Template）
     */
    private final Map<String, Template> templateCache = new HashMap<>();
    
    /**
     * Prompt模板类型
     */
    public enum PromptStage {
        /**
         * System Template：定义AI角色和通用约束
         */
        SYSTEM("SYSTEM"),
        
        /**
         * Business Template：定义特定业务的识别逻辑
         */
        EXTRACTION("EXTRACTION"),
        
        /**
         * Feedback Template：用于"反思重试"阶段
         */
        REFLECT("REFLECT");
        
        private final String value;
        
        PromptStage(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * 构建完整的Prompt
     * 
     * @param bizType 业务类型（如：GAODE、XIAOLA等）
     * @param stage Prompt阶段（SYSTEM、EXTRACTION、REFLECT）
     * @param context 上下文变量（用于模板渲染）
     * @return 渲染后的Prompt
     */
    public String buildPrompt(String bizType, PromptStage stage, Map<String, Object> context) {
        return buildPrompt(bizType, stage, context, null);
    }
    
    /**
     * 验证并清理上下文变量
     * 
     * 在构建Prompt前，对用户输入进行安全验证和过滤
     */
    private Map<String, Object> validateAndSanitizeContext(Map<String, Object> context) {
        if (context == null || inputValidator == null) {
            return context;
        }
        
        Map<String, Object> sanitizedContext = new HashMap<>(context);
        
        // 验证关键字段（如用户输入、OCR文本等）
        String[] fieldsToValidate = {"ocrData", "input", "userInput", "question"};
        
        for (String field : fieldsToValidate) {
            Object value = sanitizedContext.get(field);
            if (value != null && value instanceof String) {
                String input = (String) value;
                
                // 验证输入
                com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.security.InputValidator.ValidationResult 
                    validationResult = inputValidator.validate(input);
                
                if (!validationResult.isValid()) {
                    log.warn("上下文变量验证失败: field={}, error={}", 
                        field, validationResult.getErrorMessage());
                    
                    // 如果验证失败，使用过滤后的输入或抛出异常
                    if (validationResult.getValidatedInput() != null) {
                        sanitizedContext.put(field, validationResult.getValidatedInput());
                    } else {
                        // 如果无法修复，使用占位符
                        sanitizedContext.put(field, "[输入已过滤]");
                    }
                } else {
                    // 使用验证后的输入
                    sanitizedContext.put(field, validationResult.getValidatedInput());
                }
            }
        }
        
        return sanitizedContext;
    }
    
    /**
     * 构建完整的Prompt（支持版本号）
     * 
     * @param bizType 业务类型
     * @param stage Prompt阶段
     * @param context 上下文变量
     * @param version 模板版本（可选，用于A/B测试）
     * @return 渲染后的Prompt
     */
    public String buildPrompt(String bizType, PromptStage stage, Map<String, Object> context, String version) {
        try {
            // 1. 验证并清理上下文变量（安全检查）
            Map<String, Object> sanitizedContext = validateAndSanitizeContext(context);
            
            // 2. 获取模板
            String templateKey = buildTemplateKey(bizType, stage, version);
            Template template = getTemplate(bizType, stage, version);
            
            // 3. 渲染模板
            String prompt = template.apply(sanitizedContext);
            
            // 3. 记录日志（用于版本追踪）
            log.debug("Prompt渲染完成: bizType={}, stage={}, version={}, promptLength={}", 
                bizType, stage, version != null ? version : "latest", prompt.length());
            
            return prompt;
            
        } catch (IOException e) {
            log.error("Prompt渲染失败: bizType={}, stage={}, error={}", bizType, stage, e.getMessage(), e);
            throw new RuntimeException("Prompt渲染失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建组合Prompt（System + Business）
     * 
     * @param bizType 业务类型
     * @param context 上下文变量
     * @return 组合后的Prompt
     */
    public String buildCombinedPrompt(String bizType, Map<String, Object> context) {
        // 1. 构建System Template
        String systemPrompt = buildPrompt(bizType, PromptStage.SYSTEM, context);
        
        // 2. 构建Business Template（支持Few-shot注入）
        String businessPrompt = buildBusinessPromptWithFewShot(bizType, context);
        
        // 3. 组合
        return String.format("%s\n\n%s", systemPrompt, businessPrompt);
    }
    
    /**
     * 构建Business Prompt（支持Few-shot注入）
     * 
     * @param bizType 业务类型
     * @param context 上下文变量
     * @return Business Prompt（包含Few-shot示例）
     */
    private String buildBusinessPromptWithFewShot(String bizType, Map<String, Object> context) {
        // 1. 如果启用了示例选择器，自动选择Few-shot示例
        if (exampleSelector != null) {
            String query = (String) context.getOrDefault("ocrData", context.getOrDefault("input", ""));
            if (query != null && !query.isEmpty()) {
                // 选择Few-shot示例
                List<com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot.ExampleSelector.Example> examples = 
                    exampleSelector.selectExamples(query, 5);
                
                if (!examples.isEmpty()) {
                    // 将示例添加到上下文
                    context.put("examples", examples);
                    context.put("hasExamples", true);
                    
                    log.debug("注入Few-shot示例: bizType={}, exampleCount={}", bizType, examples.size());
                } else {
                    context.put("hasExamples", false);
                }
            }
        }
        
        // 2. 构建Business Template
        return buildPrompt(bizType, PromptStage.EXTRACTION, context);
    }
    
    /**
     * 构建反思Prompt（用于自愈重试）
     * 
     * @param bizType 业务类型
     * @param originalPrompt 原始Prompt
     * @param originalContent 原始推理结果
     * @param validationErrors 校验错误列表
     * @param attempt 重试次数
     * @return 反思Prompt
     */
    public String buildReflectPrompt(String bizType, String originalPrompt, String originalContent, 
                                     List<String> validationErrors, int attempt) {
        Map<String, Object> context = new HashMap<>();
        context.put("originalPrompt", originalPrompt);
        context.put("originalContent", originalContent);
        context.put("validationErrors", validationErrors);
        context.put("errorCount", validationErrors.size());
        context.put("attempt", attempt);
        
        // 构建错误信息列表（用于模板渲染）
        StringBuilder errorList = new StringBuilder();
        for (int i = 0; i < validationErrors.size(); i++) {
            errorList.append(String.format("%d. %s\n", i + 1, validationErrors.get(i)));
        }
        context.put("errorList", errorList.toString());
        
        return buildPrompt(bizType, PromptStage.REFLECT, context);
    }
    
    /**
     * 获取模板（带缓存）
     */
    private Template getTemplate(String bizType, PromptStage stage, String version) throws IOException {
        String templateKey = buildTemplateKey(bizType, stage, version);
        
        // 检查缓存
        if (cacheEnabled && templateCache.containsKey(templateKey)) {
            return templateCache.get(templateKey);
        }
        
        // 从配置中心获取模板
        String rawTemplate = getTemplateFromConfig(bizType, stage, version);
        
        if (rawTemplate == null || rawTemplate.isEmpty()) {
            // 如果配置中心没有，使用默认模板
            rawTemplate = getDefaultTemplate(bizType, stage);
            log.warn("使用默认模板: bizType={}, stage={}", bizType, stage);
        }
        
        // 编译模板
        Template template = handlebars.compileInline(rawTemplate);
        
        // 缓存模板
        if (cacheEnabled) {
            templateCache.put(templateKey, template);
        }
        
        return template;
    }
    
    /**
     * 从配置中心获取模板
     */
    private String getTemplateFromConfig(String bizType, PromptStage stage, String version) {
        if (appConfigService == null) {
            log.warn("AppConfigService未配置，无法从配置中心获取模板");
            return null;
        }
        
        try {
            Map<String, String> config = appConfigService.getConfig(configNamespace);
            
            // 构建配置键（支持版本）
            String configKey = buildConfigKey(bizType, stage, version);
            String template = config.get(configKey);
            
            if (template != null && !template.isEmpty()) {
                log.debug("从配置中心获取模板: key={}", configKey);
                return template;
            }
            
            // 如果没有指定版本，尝试获取最新版本
            if (version == null) {
                configKey = buildConfigKey(bizType, stage, "latest");
                template = config.get(configKey);
                if (template != null && !template.isEmpty()) {
                    return template;
                }
            }
            
        } catch (Exception e) {
            log.error("从配置中心获取模板失败: bizType={}, stage={}, error={}", 
                bizType, stage, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 构建配置键
     */
    private String buildConfigKey(String bizType, PromptStage stage, String version) {
        if (version != null && !version.isEmpty()) {
            return String.format("%s_%s_%s", bizType, stage.getValue(), version);
        }
        return String.format("%s_%s", bizType, stage.getValue());
    }
    
    /**
     * 构建模板缓存键
     */
    private String buildTemplateKey(String bizType, PromptStage stage, String version) {
        return String.format("%s_%s_%s", bizType, stage.getValue(), version != null ? version : "latest");
    }
    
    /**
     * 获取默认模板（当配置中心没有时使用）
     */
    private String getDefaultTemplate(String bizType, PromptStage stage) {
        switch (stage) {
            case SYSTEM:
                return "你是一位专业的{{role}}。请仔细分析提供的信息，确保输出结果的准确性和完整性。";
            
            case EXTRACTION:
                // 根据业务类型返回不同的默认模板
                if ("GAODE".equalsIgnoreCase(bizType)) {
                    return "请分析以下OCR文本：{{ocrData}}。\n" +
                           "重点提取【{{targetField}}】信息。\n" +
                           "要求：\n" +
                           "1. 输出JSON格式\n" +
                           "2. 确保数值准确\n" +
                           "3. 如果信息缺失，标记为null";
                } else if ("XIAOLA".equalsIgnoreCase(bizType)) {
                    return "请分析以下OCR文本：{{ocrData}}。\n" +
                           "提取关键信息，包括价格、时间、地点等。\n" +
                           "输出JSON格式。";
                } else {
                    // 通用模板
                    return "请根据图片和OCR文本进行推理：\n" +
                           "{{#if ocrData}}OCR识别结果：\n{{ocrData}}\n{{/if}}" +
                           "{{#if targetField}}重点提取：{{targetField}}\n{{/if}}" +
                           "请以JSON格式返回结果。";
                }
            
            case REFLECT:
                return "你之前的回答有误，错误原因如下：\n" +
                       "{{errorList}}\n" +
                       "请根据以上错误信息，重新核对并修正你的回答。\n" +
                       "原始任务：{{originalPrompt}}\n" +
                       "原始回答：{{originalContent}}";
            
            default:
                return "请根据提供的信息进行分析。";
        }
    }
    
    /**
     * 清除模板缓存（用于配置更新时）
     */
    public void clearCache() {
        templateCache.clear();
        log.info("Prompt模板缓存已清除");
    }
    
    /**
     * 清除指定业务的模板缓存
     */
    public void clearCache(String bizType) {
        templateCache.entrySet().removeIf(entry -> entry.getKey().startsWith(bizType + "_"));
        log.info("Prompt模板缓存已清除: bizType={}", bizType);
    }
    
    /**
     * 获取模板版本信息（用于监控和追踪）
     */
    public String getTemplateVersion(String bizType, PromptStage stage) {
        // 可以从配置中心获取版本信息
        if (appConfigService != null) {
            Map<String, String> config = appConfigService.getConfig(configNamespace);
            String versionKey = buildConfigKey(bizType, stage, "version");
            return config.getOrDefault(versionKey, "unknown");
        }
        return "unknown";
    }
    
    /**
     * 获取当前Prompt模板内容（用于优化）
     */
    public String getCurrentPromptTemplate(String bizType, PromptStage stage) {
        if (appConfigService == null) {
            return null;
        }
        
        try {
            Map<String, String> config = appConfigService.getConfig(configNamespace);
            String configKey = buildConfigKey(bizType, stage, null);
            return config.get(configKey);
        } catch (Exception e) {
            log.warn("获取当前Prompt模板失败: bizType={}, stage={}, error={}", 
                bizType, stage, e.getMessage());
            return null;
        }
    }
}
