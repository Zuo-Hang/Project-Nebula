package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.Data;
import java.util.List;

/**
 * 页面去重输入
 * 对应 Go 版本的 image.PageDedupInput
 */
@Data
public class PageDedupInput {
    /**
     * 图片路径
     */
    private String imagePath;

    /**
     * 唯一ID列表（支持多个ID）
     */
    private List<String> uniqueIDs;
}

