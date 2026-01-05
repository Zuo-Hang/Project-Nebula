package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.Data;
import java.util.List;

/**
 * 页面去重汇总
 * 对应 Go 版本的 image.PageDedupSummary
 */
@Data
public class PageDedupSummary {
    /**
     * 总页面数
     */
    private int totalPages;

    /**
     * 保留页面数
     */
    private int keptPages;

    /**
     * 移除页面数
     */
    private int removedPages;

    /**
     * 去重率
     */
    private double deduplicationRate;

    /**
     * 详细结果
     */
    private List<PageDedupResult> results;
}

