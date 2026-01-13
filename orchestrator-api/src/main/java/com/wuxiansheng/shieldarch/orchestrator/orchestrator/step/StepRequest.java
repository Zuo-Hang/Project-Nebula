package com.wuxiansheng.shieldarch.orchestrator.orchestrator.step;

import lombok.Data;

import java.util.Map;

/**
 * 步骤请求
 * 对应旧项目的 ReasonRequest
 */
@Data
public class StepRequest {
    /**
     * 步骤上下文（自定义Map）
     */
    private Map<String, Object> context;

    /**
     * 提示词（用于LLM推理）
     */
    private String prompt;

    /**
     * 图片URL（用于OCR/推理）
     */
    private String imageUrl;

    /**
     * 视频路径（用于视频处理）
     */
    private String videoPath;

    /**
     * 其他自定义参数
     */
    private Map<String, Object> params;
}

