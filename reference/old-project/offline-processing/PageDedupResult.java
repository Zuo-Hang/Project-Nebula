package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.Data;
import java.util.List;

/**
 * 页面去重结果
 * 对应 Go 版本的 image.PageDedupResult
 */
@Data
public class PageDedupResult {
    /**
     * 图片路径
     */
    private String imagePath;

    /**
     * 唯一ID列表
     */
    private List<String> uniqueIDs;

    /**
     * 动作: "kept" 或 "removed"
     */
    private String action;

    /**
     * 原因
     */
    private String reason;
}

