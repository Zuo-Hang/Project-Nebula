package com.wuxiansheng.shieldarch.marsdata.offline.image;

import java.util.List;

/**
 * 页面去重策略接口
 * 对应 Go 版本的 image.DedupStrategy
 */
public interface DedupStrategy {
    /**
     * 执行去重，返回去重结果
     * 
     * @param inputs 去重输入列表，每个输入包含页面信息和ID列表
     * @return 去重汇总结果
     */
    PageDedupSummary deduplicate(List<PageDedupInput> inputs);
}

