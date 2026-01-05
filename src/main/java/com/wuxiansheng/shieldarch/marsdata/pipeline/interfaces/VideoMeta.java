package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import lombok.Data;

/**
 * 视频元数据
 * 对应 Go 版本的 interfaces.VideoMeta
 */
@Data
public class VideoMeta {
    /**
     * 帧率
     */
    private double fps;

    /**
     * 时长（秒）
     */
    private double durationSec;

    /**
     * 宽度
     */
    private int width;

    /**
     * 高度
     */
    private int height;
}

