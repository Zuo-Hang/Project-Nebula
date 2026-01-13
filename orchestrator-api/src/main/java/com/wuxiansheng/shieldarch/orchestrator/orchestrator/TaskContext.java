package com.wuxiansheng.shieldarch.orchestrator.orchestrator;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务上下文
 * 对应旧项目的 PipelineContext 和 BusinessContext
 * 
 * 用于在任务执行过程中传递状态和数据
 */
@Data
public class TaskContext {
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务类型/业务名称
     */
    private String taskType;

    /**
     * 视频键（S3路径）
     */
    private String videoKey;

    /**
     * 链路名称
     */
    private String linkName;

    /**
     * 图片路径列表
     */
    private List<String> imagePaths;

    /**
     * 保留的图片路径列表
     */
    private List<String> keptImagePaths;

    /**
     * 本地视频路径
     */
    private String localVideoPath;

    /**
     * OCR识别结果（key: 图片路径, value: 识别文本）
     */
    private Map<String, String> ocrTextByImage;

    /**
     * 分类结果
     */
    private Object classification;

    /**
     * S3上传信息
     */
    private Object s3UploadInfo;

    /**
     * 提交日期
     */
    private String submitDate;

    /**
     * 自定义数据存储
     */
    private Map<String, Object> customData = new HashMap<>();

    /**
     * 获取自定义值
     */
    public Object get(String key) {
        return customData.get(key);
    }

    /**
     * 设置自定义值
     */
    public void set(String key, Object value) {
        customData.put(key, value);
    }

    /**
     * 获取字符串值
     */
    public String getString(String key) {
        Object value = customData.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 克隆上下文
     */
    public TaskContext clone() {
        TaskContext cloned = new TaskContext();
        cloned.setTaskId(this.taskId);
        cloned.setTaskType(this.taskType);
        cloned.setVideoKey(this.videoKey);
        cloned.setLinkName(this.linkName);
        cloned.setImagePaths(this.imagePaths != null ? List.copyOf(this.imagePaths) : null);
        cloned.setKeptImagePaths(this.keptImagePaths != null ? List.copyOf(this.keptImagePaths) : null);
        cloned.setLocalVideoPath(this.localVideoPath);
        cloned.setOcrTextByImage(this.ocrTextByImage != null ? new HashMap<>(this.ocrTextByImage) : null);
        cloned.setClassification(this.classification);
        cloned.setS3UploadInfo(this.s3UploadInfo);
        cloned.setSubmitDate(this.submitDate);
        cloned.setCustomData(new HashMap<>(this.customData));
        return cloned;
    }
}

