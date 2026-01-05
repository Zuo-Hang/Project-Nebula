package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 分类汇总
 * 对应 Go 版本的 interfaces.ClassificationSummary
 */
@Data
public class ClassificationSummary {
    /**
     * 类型计数（key: 页面类型, value: 数量）
     */
    private Map<String, Integer> typeCounts;

    /**
     * 分类结果（key: 页面类型, value: 结果列表）
     */
    private Map<String, List<ClassificationResult>> results;
}

