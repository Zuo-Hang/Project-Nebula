package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Pipeline上下文接口
 * 对应 Go 版本的 interfaces.PipelineContext
 */
public interface PipelineContext {
    /**
     * 获取值（通用方法）
     */
    Object get(ContextKey key);

    /**
     * 设置值（通用方法）
     */
    void set(ContextKey key, Object value);

    /**
     * 获取字符串值
     */
    String getString(ContextKey key);

    /**
     * 获取字符串列表
     */
    List<String> getStringList(ContextKey key);

    // 便捷方法

    String getVideoKey();
    void setVideoKey(String videoKey);

    String getLinkName();
    void setLinkName(String linkName);

    List<String> getImagePaths();
    void setImagePaths(List<String> imagePaths);

    String getLocalVideo();
    void setLocalVideo(String localVideo);

    List<String> getVideoKeys();
    void setVideoKeys(List<String> videoKeys);

    List<String> getKeptImagePaths();
    void setKeptImagePaths(List<String> imagePaths);

    Map<String, String> getOCRTextByImage();
    void setOCRTextByImage(Map<String, String> textByImage);

    Object getClassification();
    void setClassification(Object classification);

    Object getS3UploadInfo();
    void setS3UploadInfo(Object uploadInfo);

    Object getPageTypeDedup();
    void setPageTypeDedup(Object pageTypeDedup);

    List<String> getCleanupPaths();
    void setCleanupPaths(List<String> paths);

    /**
     * 克隆上下文
     */
    PipelineContext clone();
}

