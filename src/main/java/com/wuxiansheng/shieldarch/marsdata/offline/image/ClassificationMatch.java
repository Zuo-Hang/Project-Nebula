package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.Data;
import java.util.List;

/**
 * 页面分类匹配结果
 * 对应 Go 版本的 image.ClassificationMatch
 */
@Data
public class ClassificationMatch {
    /**
     * 页面类型
     */
    private String pageType;

    /**
     * 匹配到的关键词列表
     */
    private List<String> matchedKeywords;

    /**
     * 匹配的关键词数量
     */
    private int matchCount;
}

