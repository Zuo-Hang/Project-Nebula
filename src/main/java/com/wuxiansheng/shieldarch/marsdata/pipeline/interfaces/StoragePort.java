package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

import java.util.List;

/**
 * 对象存储接口
 * 对应 Go 版本的 interfaces.StoragePort
 */
public interface StoragePort {
    /**
     * 列出对象
     * 
     * @param prefix 前缀
     * @return 对象键列表
     * @throws Exception 处理失败时抛出异常
     */
    List<String> listObjects(String prefix) throws Exception;

    /**
     * 下载对象
     * 
     * @param objectKey 对象键
     * @param localPath 本地路径
     * @throws Exception 处理失败时抛出异常
     */
    void download(String objectKey, String localPath) throws Exception;

    /**
     * 上传对象
     * 
     * @param localPath 本地路径
     * @param objectKey 对象键
     * @throws Exception 处理失败时抛出异常
     */
    void upload(String localPath, String objectKey) throws Exception;

    /**
     * 生成预签名URL
     * 
     * @param objectKey 对象键
     * @return 预签名URL
     * @throws Exception 处理失败时抛出异常
     */
    String presign(String objectKey) throws Exception;
}

