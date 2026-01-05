package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 列举 S3 前缀下的子目录与视频（非递归合并）
 * 对应 Go 版本的 stages.ListStage
 */
@Slf4j
public class ListStage implements PipelineStage {

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    private String bucket;
    private String prefix;

    public ListStage() {
    }

    public ListStage(S3Client s3Client, String bucket, String prefix) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    @Override
    public String name() {
        return "List";
    }

    @Override
    public String describe() {
        return "List videos from storage";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        // 兼容新配置：若未显式设置 Bucket，则尝试根据链路名与基础配置解析
        String bucketName = this.bucket;
        if (bucketName == null || bucketName.isEmpty()) {
            String pipelineName = pipelineCtx.getLinkName();
            if (pipelineName != null && !pipelineName.isEmpty() && configService != null) {
                VideoFrameExtractionConfig cfg = configService.getVideoFrameExtractionConfig(pipelineName);
                if (cfg != null && cfg.getInput() != null) {
                    // TODO: 从基础配置中获取 bucket 名称
                    // 这里需要访问 BaseConfig.Storage，暂时使用配置中的 storage 名称
                    bucketName = cfg.getInput().getStorage();
                }
            }
            if (bucketName == null || bucketName.isEmpty()) {
                throw new Exception("[List] bucket 未设置且无法从配置解析，请在上游填写或预先加载配置");
            }
        }

        String prefixValue = this.prefix;
        if (prefixValue == null || prefixValue.isEmpty()) {
            throw new Exception("[List] prefix 未设置（多链路场景请在上游构建 prefix）");
        }

        log.info("[List] 请求参数 | bucket={} | prefix={}", bucketName, prefixValue);

        if (s3Client == null) {
            throw new Exception("[List] S3Client 未配置");
        }

        // 列出目录
        List<String> dirs = s3Client.listDirectories(bucketName, prefixValue);
        log.info("[List] 发现子目录: {} 个", dirs.size());
        if (!dirs.isEmpty()) {
            log.info("[List] 子目录清单: {}", String.join(", ", dirs));
        }

        List<String> videos = new ArrayList<>();
        if (dirs.isEmpty()) {
            log.info("[List] 无子目录，直接列举当前前缀下文件(非递归)");
            List<String> files = s3Client.listFiles(bucketName, prefixValue);
            videos.addAll(filterVideosByExt(files));
        } else {
            for (String d : dirs) {
                String dirPrefix = prefixValue;
                if (!dirPrefix.isEmpty() && !dirPrefix.endsWith("/")) {
                    dirPrefix += "/";
                }
                dirPrefix += d + "/";
                List<String> files = s3Client.listFiles(bucketName, dirPrefix);
                videos.addAll(filterVideosByExt(files));
            }
        }
        log.info("[List] 汇总视频数: {}", videos.size());
        if (!videos.isEmpty()) {
            log.info("[List] 视频清单:\n{}", String.join("\n", videos));
        }

        // 将结果写入 Context
        pipelineCtx.setVideoKeys(videos);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 使用基础配置 file_types.video_extensions 过滤目标列表
     */
    private List<String> filterVideosByExt(List<String> keys) {
        // TODO: 从配置中获取视频扩展名列表
        Set<String> videoExtensions = new HashSet<>();
        videoExtensions.add(".mp4");
        videoExtensions.add(".avi");
        videoExtensions.add(".mov");
        videoExtensions.add(".mkv");

        return keys.stream()
            .filter(key -> {
                String ext = getFileExtension(key).toLowerCase();
                return videoExtensions.contains(ext);
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }
}

