package com.wuxiansheng.shieldarch.marsdata.pipeline.context;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 视频抽帧链路上下文实现
 * 对应 Go 版本的 context.VideoFrameExtractionContext
 */
@Slf4j
public class VideoFrameExtractionContext implements PipelineContext {

    private final Map<ContextKey, Object> data = new HashMap<>();

    public VideoFrameExtractionContext() {
    }

    public VideoFrameExtractionContext(VideoFrameExtractionContext other) {
        this.data.putAll(other.data);
    }

    @Override
    public Object get(ContextKey key) {
        return data.get(key);
    }

    @Override
    public void set(ContextKey key, Object value) {
        data.put(key, value);
    }

    @Override
    public String getString(ContextKey key) {
        Object value = data.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return value != null ? value.toString() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStringList(ContextKey key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    @Override
    public String getVideoKey() {
        return getString(ContextKey.VIDEO_KEY);
    }

    @Override
    public void setVideoKey(String videoKey) {
        set(ContextKey.VIDEO_KEY, videoKey);
    }

    @Override
    public String getLinkName() {
        return getString(ContextKey.LINK_NAME);
    }

    @Override
    public void setLinkName(String linkName) {
        set(ContextKey.LINK_NAME, linkName);
    }

    @Override
    public List<String> getImagePaths() {
        return getStringList(ContextKey.IMAGE_PATHS);
    }

    @Override
    public void setImagePaths(List<String> imagePaths) {
        set(ContextKey.IMAGE_PATHS, imagePaths);
    }

    @Override
    public String getLocalVideo() {
        return getString(ContextKey.LOCAL_VIDEO);
    }

    @Override
    public void setLocalVideo(String localVideo) {
        set(ContextKey.LOCAL_VIDEO, localVideo);
    }

    @Override
    public List<String> getVideoKeys() {
        return getStringList(ContextKey.VIDEO_KEYS);
    }

    @Override
    public void setVideoKeys(List<String> videoKeys) {
        set(ContextKey.VIDEO_KEYS, videoKeys);
    }

    @Override
    public List<String> getKeptImagePaths() {
        return getStringList(ContextKey.KEPT_IMAGE_PATHS);
    }

    @Override
    public void setKeptImagePaths(List<String> imagePaths) {
        set(ContextKey.KEPT_IMAGE_PATHS, imagePaths);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getOCRTextByImage() {
        Object value = data.get(ContextKey.OCR_TEXT_BY_IMAGE);
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        return Collections.emptyMap();
    }

    @Override
    public void setOCRTextByImage(Map<String, String> textByImage) {
        set(ContextKey.OCR_TEXT_BY_IMAGE, textByImage);
    }

    @Override
    public Object getClassification() {
        return data.get(ContextKey.CLASSIFICATION);
    }

    @Override
    public void setClassification(Object classification) {
        set(ContextKey.CLASSIFICATION, classification);
    }

    @Override
    public Object getS3UploadInfo() {
        return data.get(ContextKey.S3_UPLOAD_INFO);
    }

    @Override
    public void setS3UploadInfo(Object uploadInfo) {
        set(ContextKey.S3_UPLOAD_INFO, uploadInfo);
    }

    @Override
    public Object getPageTypeDedup() {
        return data.get(ContextKey.PAGE_TYPE_DEDUP);
    }

    @Override
    public void setPageTypeDedup(Object pageTypeDedup) {
        set(ContextKey.PAGE_TYPE_DEDUP, pageTypeDedup);
    }

    @Override
    public List<String> getCleanupPaths() {
        return getStringList(ContextKey.CLEANUP_PATHS);
    }

    @Override
    public void setCleanupPaths(List<String> paths) {
        set(ContextKey.CLEANUP_PATHS, paths);
    }

    @Override
    public PipelineContext clone() {
        return new VideoFrameExtractionContext(this);
    }
}

