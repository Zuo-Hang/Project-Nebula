package com.wuxiansheng.shieldarch.marsdata.offline.text;

import java.util.List;

/**
 * ID生成策略接口
 * 对应 Go 版本的 text.IDStrategy
 */
public interface IDStrategy {
    /**
     * 生成ID列表
     * 
     * @param ocrText OCR识别的文本内容
     * @param patterns 正则表达式列表
     * @return 生成的ID列表（通常只有一个）
     * @throws Exception 生成失败时抛出异常
     */
    List<String> generateIDs(String ocrText, List<String> patterns) throws Exception;
}

