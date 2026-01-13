package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 清理阶段
 * 对应 Go 版本的 stages.CleanupStage
 */
@Slf4j
public class CleanupStage implements PipelineStage {

    @Override
    public String name() {
        return "Cleanup";
    }

    @Override
    public String describe() {
        return "Cleanup local temporary files";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        log.info("[CleanupStage] 7. 清理本地文件...");
        long started = System.currentTimeMillis();

        List<String> targets = new ArrayList<>();

        // 从 Context 获取需要清理的路径
        List<String> cleanupPaths = pipelineCtx.getCleanupPaths();
        if (cleanupPaths != null) {
            targets.addAll(cleanupPaths);
        }

        // 从 Context 获取 LocalVideo 和 ImagePaths
        String localVideo = pipelineCtx.getLocalVideo();
        List<String> imagePaths = pipelineCtx.getImagePaths();

        if (localVideo != null && !localVideo.isEmpty()) {
            File videoFile = new File(localVideo);
            File videoDir = videoFile.getParentFile();
            if (videoDir != null) {
                String tempDir = System.getProperty("java.io.tmpdir");
                if (tempDir != null && videoDir.getAbsolutePath().startsWith(tempDir)) {
                    targets.add(videoDir.getAbsolutePath());
                }
            }
            if (videoFile.exists()) {
                targets.add(localVideo);
            }
        }

        if (imagePaths != null && !imagePaths.isEmpty()) {
            File firstImage = new File(imagePaths.get(0));
            File extractDir = firstImage.getParentFile();
            if (extractDir != null) {
                targets.add(extractDir.getAbsolutePath());
            }
        }

        List<String> deleted = new ArrayList<>();
        for (String target : targets) {
            if (target == null || target.isEmpty()) {
                continue;
            }

            Path path = Paths.get(target);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                if (Files.isDirectory(path)) {
                    deleteDirectory(path);
                } else {
                    Files.delete(path);
                }
                deleted.add(target);
            } catch (Exception e) {
                log.warn("删除失败 {}: {}", target, e.getMessage());
            }
        }

        if (!deleted.isEmpty()) {
            log.info("已删除: {}", String.join(", ", deleted));
        }

        long duration = System.currentTimeMillis() - started;
        log.info("本地文件清理完成，耗时: {}ms", duration);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws Exception {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        log.warn("删除文件失败: {}", path, e);
                    }
                });
        }
    }
}

