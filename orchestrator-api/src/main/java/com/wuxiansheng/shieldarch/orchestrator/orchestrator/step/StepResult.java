package com.wuxiansheng.shieldarch.orchestrator.orchestrator.step;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 步骤结果
 * 对应旧项目的 ReasonResponse
 */
@Data
public class StepResult {
    /**
     * 步骤上下文（与请求中的context对应）
     */
    private Map<String, Object> context;

    /**
     * 步骤执行结果内容（JSON格式字符串，用于LLM推理结果）
     */
    private String content;

    /**
     * 错误信息
     */
    private Exception error;

    /**
     * 图片路径列表（用于视频抽帧结果）
     */
    private List<String> imagePaths;

    /**
     * OCR识别结果（key: 图片路径, value: 识别文本）
     */
    private Map<String, String> ocrTextByImage;

    /**
     * 其他自定义数据
     */
    private Map<String, Object> data;

    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return error == null;
    }
}

