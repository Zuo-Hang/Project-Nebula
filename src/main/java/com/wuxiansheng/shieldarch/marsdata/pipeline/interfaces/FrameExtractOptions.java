package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import lombok.Data;

/**
 * 视频抽帧选项
 * 对应 Go 版本的 interfaces.FrameExtractOptions
 */
@Data
public class FrameExtractOptions {
    /**
     * 抽帧间隔（秒）
     */
    private double intervalSeconds;

    /**
     * 是否使用关键帧
     */
    private boolean useKeyFrames;

    /**
     * 抽帧输出目录
     */
    private String outputDir;

    /**
     * 起始毫秒位置
     */
    private long startMillis;

    /**
     * 最大抽帧数量（0表示无限制）
     */
    private int maxFrames;

    /**
     * 图片质量（FFmpeg quality参数）
     */
    private int quality;

    /**
     * FFmpeg线程数
     */
    private int threads;
}

