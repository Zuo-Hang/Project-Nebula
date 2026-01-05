package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import java.util.List;

/**
 * 视频处理接口
 * 对应 Go 版本的 interfaces.VideoPort
 */
public interface VideoPort {
    /**
     * 探测视频信息
     * 
     * @param localVideoPath 本地视频路径
     * @return 视频元数据
     * @throws Exception 处理失败时抛出异常
     */
    VideoMeta probe(String localVideoPath) throws Exception;

    /**
     * 提取视频帧
     * 
     * @param localVideoPath 本地视频路径
     * @param options 抽帧选项
     * @return 提取的图片路径列表
     * @throws Exception 处理失败时抛出异常
     */
    List<String> extractFrames(String localVideoPath, FrameExtractOptions options) throws Exception;
}

