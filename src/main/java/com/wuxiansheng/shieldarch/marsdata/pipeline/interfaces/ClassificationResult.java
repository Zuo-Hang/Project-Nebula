package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import lombok.Data;
import java.util.List;

/**
 * 分类结果
 * 对应 Go 版本的 interfaces.ClassificationResult
 */
@Data
public class ClassificationResult {
    /**
     * 图片路径
     */
    private String imagePath;

    /**
     * 页面类型
     */
    private String pageType;

    /**
     * 置信度
     */
    private double confidence;

    /**
     * OCR识别的文本内容
     */
    private String ocrText;

    /**
     * 匹配到的关键字
     */
    private List<String> matchedKeywords;

    /**
     * 处理耗时（毫秒）
     */
    private long processTime;

    /**
     * 错误信息（如果有）
     */
    private Exception error;
}

