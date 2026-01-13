package com.wuxiansheng.shieldarch.orchestrator.orchestrator;

/**
 * LLM服务客户端接口
 * 
 * 用于调用LLM进行推理
 */
public interface LLMServiceClient {
    /**
     * 执行LLM推理
     * 
     * @param prompt 提示词
     * @param imageUrl 图片URL（可选）
     * @param ocrText OCR文本（可选）
     * @return LLM推理结果
     * @throws Exception 推理失败时抛出异常
     */
    String infer(String prompt, String imageUrl, String ocrText) throws Exception;
}

