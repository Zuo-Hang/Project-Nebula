package com.wuxiansheng.shieldarch.marsdata.pipeline.storage;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.StoragePort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * S3存储端口实现
 * 对应 Go 版本的 adapters/storage/s3.Client
 */
@Slf4j
public class S3StoragePort implements StoragePort {

    private final S3Client s3Client;
    private final String bucketName;

    /**
     * 从链路配置的 input.storage 获取 bucket（用于下载）
     */
    public static S3StoragePort newWithLinkNameForInput(S3Client s3Client, String linkName, 
                                                         VideoFrameExtractionConfigService configService) {
        String bucket = getBucketFromStorage(linkName, true, configService);
        return new S3StoragePort(s3Client, bucket);
    }

    /**
     * 从链路配置的 output.storage 获取 bucket（用于上传）
     */
    public static S3StoragePort newWithLinkNameForOutput(S3Client s3Client, String linkName,
                                                          VideoFrameExtractionConfigService configService) {
        String bucket = getBucketFromStorage(linkName, false, configService);
        return new S3StoragePort(s3Client, bucket);
    }

    public S3StoragePort(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public List<String> listObjects(String prefix) throws Exception {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new Exception("s3 default bucket not configured");
        }
        if (s3Client == null) {
            throw new Exception("S3Client not configured");
        }
        return s3Client.listFiles(bucketName, prefix);
    }

    @Override
    public void download(String objectKey, String localPath) throws Exception {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new Exception("s3 default bucket not configured");
        }
        if (s3Client == null) {
            throw new Exception("S3Client not configured");
        }
        s3Client.downloadFile(bucketName, objectKey, localPath);
    }

    @Override
    public void upload(String localPath, String objectKey) throws Exception {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new Exception("s3 default bucket not configured");
        }
        if (s3Client == null) {
            throw new Exception("S3Client not configured");
        }
        s3Client.uploadFile(bucketName, localPath, objectKey);
    }

    @Override
    public String presign(String objectKey) throws Exception {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new Exception("s3 default bucket not configured");
        }
        if (s3Client == null) {
            throw new Exception("S3Client not configured");
        }
        return s3Client.getFileURL(bucketName, objectKey);
    }

    /**
     * 从配置获取 bucket
     */
    private static String getBucketFromStorage(String linkName, boolean isInput, 
                                               VideoFrameExtractionConfigService configService) {
        if (configService == null) {
            throw new RuntimeException("VideoFrameExtractionConfigService not configured");
        }

        VideoFrameExtractionConfig config = configService.getVideoFrameExtractionConfig(linkName);
        if (config == null) {
            throw new RuntimeException("链路配置不存在: " + linkName);
        }

        String storageName;
        if (isInput) {
            storageName = config.getInput() != null ? config.getInput().getStorage() : null;
        } else {
            storageName = config.getOutput() != null ? config.getOutput().getStorage() : null;
        }

        if (storageName == null || storageName.isEmpty()) {
            throw new RuntimeException("存储配置未找到: linkName=" + linkName + ", isInput=" + isInput);
        }

        // TODO: 从BaseConfig中获取实际的bucket名称
        // 这里暂时返回storage名称，实际应该从BaseConfig.Storage中查找
        return storageName;
    }
}

