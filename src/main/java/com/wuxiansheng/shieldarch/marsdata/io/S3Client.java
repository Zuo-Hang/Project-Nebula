package com.wuxiansheng.shieldarch.marsdata.io;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * S3客户端服务
 */
@Slf4j
@Service
public class S3Client {
    
    /**
     * S3客户端映射（按名称）
     */
    private final Map<String, MinioClient> clients = new ConcurrentHashMap<>();
    
    /**
     * 默认S3客户端
     */
    private MinioClient defaultClient;
    
    /**
     * S3运行时配置
     */
    @Autowired(required = false)
    private S3RuntimeConfig runtimeConfig;
    
    /**
     * 初始化S3客户端
     */
    public void initClients(List<S3StorageConfig> storageConfigs) {
        if (storageConfigs == null || storageConfigs.isEmpty()) {
            log.warn("S3存储配置为空，无法初始化客户端");
            return;
        }
        
        for (S3StorageConfig config : storageConfigs) {
            try {
                MinioClient client = MinioClient.builder()
                    .endpoint(config.getEndpoint())
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .build();
                
                if (config.getName() != null && !config.getName().isEmpty()) {
                    clients.put(config.getName(), client);
                } else {
                    defaultClient = client;
                }
            } catch (Exception e) {
                log.error("初始化S3客户端失败: endpoint={}, name={}, error={}", 
                    config.getEndpoint(), config.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * 获取S3客户端
     */
    private MinioClient getClient(String bucketName) {
        if (bucketName != null && clients.containsKey(bucketName)) {
            return clients.get(bucketName);
        }
        return defaultClient;
    }
    
    /**
     * 列出指定路径下的所有文件（非递归）
     */
    public List<String> listFiles(String bucketName, String prefix) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        List<Result<Item>> objects = listObjectsNonRecursiveWithRetry(client, bucketName, prefix);
        
        return objects.stream()
            .map(item -> {
                try {
                    return item.get().objectName();
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(name -> name != null && !name.endsWith("/"))
            .collect(Collectors.toList());
    }
    
    /**
     * 列出指定路径下的所有目录名称
     */
    public List<String> listDirectories(String bucketName, String prefix) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        List<Result<Item>> objects = listObjectsNonRecursiveWithRetry(client, bucketName, prefix);
        
        Set<String> directorySet = new HashSet<>();
        for (Result<Item> itemResult : objects) {
            try {
                Item item = itemResult.get();
                String relativePath = item.objectName();
                if (prefix != null && relativePath.startsWith(prefix)) {
                    relativePath = relativePath.substring(prefix.length());
                }
                
                if (relativePath.isEmpty()) {
                    continue;
                }
                
                if (relativePath.endsWith("/")) {
                    String dirName = relativePath.substring(0, relativePath.length() - 1);
                    if (!dirName.isEmpty() && !directorySet.contains(dirName)) {
                        directorySet.add(dirName);
                    }
                }
            } catch (Exception e) {
                log.warn("处理目录项失败: {}", e.getMessage());
            }
        }
        
        List<String> directories = new ArrayList<>(directorySet);
        Collections.sort(directories);
        return directories;
    }
    
    /**
     * 非递归列出对象（支持重试）
     */
    private List<Result<Item>> listObjectsNonRecursiveWithRetry(
            MinioClient client, String bucketName, String prefix) throws Exception {
        
        int maxRetry = runtimeConfig.getListMaxRetry();
        Duration timeout = runtimeConfig.getListTimeout();
        Duration retryDelay = runtimeConfig.getListRetryDelay();
        
        Exception lastErr = null;
        List<Result<Item>> out = new ArrayList<>();
        
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(false)
                        .build()
                );
                
                out.clear();
                boolean hasErr = false;
                
                for (Result<Item> result : results) {
                    try {
                        result.get(); // 触发可能的异常
                        out.add(result);
                    } catch (Exception e) {
                        hasErr = true;
                        lastErr = e;
                        log.warn("列出对象时出错: {}", e.getMessage());
                    }
                }
                
                if (!hasErr) {
                    break;
                }
                
            } catch (Exception e) {
                lastErr = e;
                log.warn("列出对象失败 (尝试 {}/{}): {}", attempt, maxRetry, e.getMessage());
            }
            
            if (attempt < maxRetry) {
                Thread.sleep(retryDelay.toMillis());
            }
        }
        
        if (out.isEmpty() && lastErr != null) {
            throw lastErr;
        }
        
        return out;
    }
    
    /**
     * 获取S3对象的输入流（用于流式处理，不下载到本地）
     * 
     * @param bucketName 桶名称
     * @param objectKey 对象键
     * @return 输入流，使用完毕后需要关闭
     * @throws Exception 获取失败时抛出异常
     */
    public java.io.InputStream getObjectStream(String bucketName, String objectKey) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        int maxRetries = runtimeConfig.getDownloadMaxRetries();
        Duration retryDelay = runtimeConfig.getDownloadRetryDelay();
        double backoffMultiplier = runtimeConfig.getDownloadBackoffMultiplier();
        
        Exception lastErr = null;
        long delayMs = retryDelay.toMillis();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return client.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build()
                );
                
            } catch (Exception e) {
                lastErr = e;
                
                if (!isRetryableError(e)) {
                    break;
                }
                
                if (attempt < maxRetries) {
                    log.warn("获取S3流失败，准备重试: {} (第{}次重试，{}ms后重试) - {}", 
                        objectKey, attempt + 1, delayMs, e.getMessage());
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * backoffMultiplier);
                }
            }
        }
        
        throw new Exception(String.format("获取S3流失败，已重试%d次: %s", 
            maxRetries, lastErr != null ? lastErr.getMessage() : "未知错误"));
    }
    
    /**
     * 下载文件，支持重试
     */
    public void downloadFile(String bucketName, String objectKey, String filePath) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        int maxRetries = runtimeConfig.getDownloadMaxRetries();
        Duration retryDelay = runtimeConfig.getDownloadRetryDelay();
        double backoffMultiplier = runtimeConfig.getDownloadBackoffMultiplier();
        
        Exception lastErr = null;
        long delayMs = retryDelay.toMillis();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                client.downloadObject(
                    DownloadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .filename(filePath)
                        .build()
                );
                
                if (attempt > 0) {
                    log.info("下载重试成功: {} (第{}次重试)", new File(objectKey).getName(), attempt);
                }
                return;
                
            } catch (Exception e) {
                lastErr = e;
                
                if (!isRetryableError(e)) {
                    break;
                }
                
                if (attempt < maxRetries) {
                    log.warn("下载失败，准备重试: {} (第{}次重试，{}ms后重试) - {}", 
                        new File(objectKey).getName(), attempt + 1, delayMs, e.getMessage());
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * backoffMultiplier);
                }
            }
        }
        
        throw new Exception(String.format("下载失败，已重试%d次: %s", maxRetries, lastErr != null ? lastErr.getMessage() : "未知错误"));
    }
    
    /**
     * 上传单个文件
     */
    public void uploadFile(String bucketName, String localPath, String objectKey) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        File file = new File(localPath);
        if (!file.exists()) {
            throw new Exception(String.format("本地文件不存在: %s", localPath));
        }
        
        int maxRetries = runtimeConfig.getUploadMaxRetries();
        Duration retryDelay = runtimeConfig.getUploadRetryDelay();
        double backoffMultiplier = runtimeConfig.getUploadBackoffMultiplier();
        Duration uploadTimeout = runtimeConfig.getUploadTimeout();
        String contentType = runtimeConfig.getUploadContentType();
        
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new Exception("S3 配置 UploadContentType 不能为空");
        }
        
        Exception lastErr = null;
        long delayMs = retryDelay.toMillis();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                client.uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .filename(localPath)
                        .contentType(contentType)
                        .build()
                );
                
                if (attempt > 0) {
                    log.info("上传重试成功: {} (第{}次重试)", file.getName(), attempt);
                }
                return;
                
            } catch (Exception e) {
                lastErr = e;
                
                if (!isRetryableError(e)) {
                    break;
                }
                
                if (attempt < maxRetries) {
                    log.warn("上传失败，准备重试: {} (第{}次重试，{}ms后重试) - {}", 
                        file.getName(), attempt + 1, delayMs, e.getMessage());
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * backoffMultiplier);
                }
            }
        }
        
        throw new Exception(String.format("上传失败，已重试%d次: %s", maxRetries, lastErr != null ? lastErr.getMessage() : "未知错误"));
    }
    
    /**
     * 批量并发上传文件
     */
    public List<String> uploadFiles(String bucketName, Map<String, String> files) throws Exception {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            throw new Exception(String.format("S3 client not configured for bucket=%s", bucketName));
        }
        
        if (runtimeConfig == null) {
            throw new Exception("S3 客户端未初始化（配置错误）");
        }
        
        List<UploadTask> tasks = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            tasks.add(new UploadTask(entry.getKey(), entry.getValue()));
        }
        
        List<UploadResult> results = uploadFilesConcurrently(client, bucketName, tasks);
        
        List<String> urls = new ArrayList<>();
        for (UploadResult result : results) {
            if (result.getError() == null && result.getUrl() != null) {
                urls.add(result.getUrl());
            } else if (result.getError() != null) {
                log.warn("上传失败: {} - {}", result.getS3ObjectKey(), result.getError().getMessage());
            }
        }
        
        return urls;
    }
    
    /**
     * 并发上传文件
     */
    private List<UploadResult> uploadFilesConcurrently(
            MinioClient client, String bucketName, List<UploadTask> tasks) {
        
        int maxConcurrency = runtimeConfig.getUploadMaxConcurrency();
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        List<CompletableFuture<UploadResult>> futures = new ArrayList<>();
        
        for (UploadTask task : tasks) {
            CompletableFuture<UploadResult> future = CompletableFuture.supplyAsync(() -> 
                uploadSingleFile(client, bucketName, task), executor);
            futures.add(future);
        }
        
        List<UploadResult> results = new ArrayList<>();
        for (CompletableFuture<UploadResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                results.add(new UploadResult(null, null, e));
            }
        }
        
        executor.shutdown();
        return results;
    }
    
    /**
     * 上传单个文件（内部方法）
     */
    private UploadResult uploadSingleFile(MinioClient client, String bucketName, UploadTask task) {
        File file = new File(task.getLocalPath());
        if (!file.exists()) {
            return new UploadResult(task.getS3ObjectKey(), null, 
                new Exception("本地文件不存在"));
        }
        
        int maxRetries = runtimeConfig.getUploadMaxRetries();
        Duration retryDelay = runtimeConfig.getUploadRetryDelay();
        double backoffMultiplier = runtimeConfig.getUploadBackoffMultiplier();
        Duration uploadTimeout = runtimeConfig.getUploadTimeout();
        String contentType = runtimeConfig.getUploadContentType();
        
        if (contentType == null || contentType.trim().isEmpty()) {
            return new UploadResult(task.getS3ObjectKey(), null, 
                new Exception("S3 配置 UploadContentType 不能为空"));
        }
        
        Exception lastErr = null;
        long delayMs = retryDelay.toMillis();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                client.uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(task.getS3ObjectKey())
                        .filename(task.getLocalPath())
                        .contentType(contentType)
                        .build()
                );
                
                String url = generatePublicURL(client, bucketName, task.getS3ObjectKey());
                
                if (attempt > 0) {
                    log.info("重试成功: {} (第{}次重试)", file.getName(), attempt);
                }
                
                return new UploadResult(task.getS3ObjectKey(), url, null);
                
            } catch (Exception e) {
                lastErr = e;
                
                if (!isRetryableError(e)) {
                    break;
                }
                
                if (attempt < maxRetries) {
                    log.warn("上传失败，准备重试: {} (第{}次重试，{}ms后重试) - {}", 
                        file.getName(), attempt + 1, delayMs, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new UploadResult(task.getS3ObjectKey(), null, ie);
                    }
                    delayMs = (long) (delayMs * backoffMultiplier);
                }
            }
        }
        
        return new UploadResult(task.getS3ObjectKey(), null, 
            new Exception(String.format("上传失败，已重试%d次: %s", maxRetries, 
                lastErr != null ? lastErr.getMessage() : "未知错误")));
    }
    
    /**
     * 生成公共访问URL
     */
    private String generatePublicURL(MinioClient client, String bucketName, String objectKey) {
        try {
            // 先检查对象是否存在
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
            
            // 生成预签名URL
            String url = client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry((int) runtimeConfig.getUrlPresignedExpiry().getSeconds())
                    .build()
            );
            
            // 移除查询参数（只保留基础URL）
            int idx = url.indexOf("?");
            if (idx != -1) {
                url = url.substring(0, idx);
            }
            
            return url;
            
        } catch (Exception e) {
            log.error("生成公共URL失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取文件的公共访问URL
     */
    public String getFileURL(String bucketName, String objectKey) {
        MinioClient client = getClient(bucketName);
        if (client == null) {
            return "";
        }
        
        if (runtimeConfig == null) {
            return "";
        }
        
        try {
            String url = client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry((int) runtimeConfig.getUrlPresignedExpiry().getSeconds())
                    .build()
            );
            
            int idx = url.indexOf("?");
            if (idx != -1) {
                url = url.substring(0, idx);
            }
            
            return url;
        } catch (Exception e) {
            log.error("获取文件URL失败: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * 判断错误是否可重试
     */
    private boolean isRetryableError(Exception err) {
        if (err == null) {
            return false;
        }
        
        String errMsg = err.getMessage().toLowerCase();
        
        // 检查配置的可重试错误
        if (runtimeConfig != null && runtimeConfig.getRetryableErrors() != null) {
            for (String retryableErr : runtimeConfig.getRetryableErrors()) {
                if (errMsg.contains(retryableErr.toLowerCase())) {
                    return true;
                }
            }
        }
        
        // 检查HTTP状态码
        if (errMsg.contains("500") || errMsg.contains("502") || 
            errMsg.contains("503") || errMsg.contains("504")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * S3存储配置
     */
    @Data
    public static class S3StorageConfig {
        private String name;
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private Boolean useSSL;
    }
    
    /**
     * S3运行时配置
     */
    @Data
    public static class S3RuntimeConfig {
        private int listMaxRetry;
        private Duration listTimeout;
        private Duration listRetryDelay;
        private int downloadMaxRetries;
        private Duration downloadRetryDelay;
        private double downloadBackoffMultiplier;
        private int uploadMaxConcurrency;
        private Duration uploadTimeout;
        private int uploadMaxRetries;
        private Duration uploadRetryDelay;
        private double uploadBackoffMultiplier;
        private String uploadContentType;
        private Duration urlPresignedExpiry;
        private List<String> retryableErrors;
    }
    
    /**
     * 上传任务
     */
    @Data
    public static class UploadTask {
        private String localPath;
        private String s3ObjectKey;
        
        public UploadTask(String localPath, String s3ObjectKey) {
            this.localPath = localPath;
            this.s3ObjectKey = s3ObjectKey;
        }
    }
    
    /**
     * 上传结果
     */
    @Data
    public static class UploadResult {
        private String s3ObjectKey;
        private String url;
        private Exception error;
        
        public UploadResult(String s3ObjectKey, String url, Exception error) {
            this.s3ObjectKey = s3ObjectKey;
            this.url = url;
            this.error = error;
        }
    }
}

