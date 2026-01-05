package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import java.util.Map;

/**
 * OCR识别接口
 * 对应 Go 版本的 interfaces.OCRPort
 */
public interface OCRPort {
    /**
     * 批量OCR识别
     * 
     * @param imagePaths 图片路径列表
     * @return 识别结果 map（key: 图片路径, value: 识别文本）
     * @throws Exception 处理失败时抛出异常
     */
    Map<String, String> recognizeBatch(java.util.List<String> imagePaths) throws Exception;
}

