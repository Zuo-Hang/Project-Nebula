package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import lombok.Data;

/**
 * S3上传信息
 * 对应 Go 版本的 interfaces.S3UploadInfo
 */
@Data
public class S3UploadInfo {
    /**
     * 上传成功数量
     */
    private int uploadedCount;

    /**
     * 失败数量
     */
    private int failedCount;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * S3 URL前缀
     */
    private String s3URLPrefix;
}

