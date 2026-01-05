package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.pipeline.context.VideoFrameExtractionContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多链路视频列表获取（复用ListStage）
 * 对应 Go 版本的 stages.MultiLinkListStage
 */
@Slf4j
public class MultiLinkListStage implements PipelineStage {

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    private String date;
    private String links; // 逗号分隔的链路名称
    private int currentConcurrentTask; // 当前并发任务索引（从0开始）
    private int maxConcurrentTasks; // 最大并发任务数（-1表示使用配置文件中的值）

    public MultiLinkListStage() {
    }

    public MultiLinkListStage(S3Client s3Client, String date, String links, 
                             int currentConcurrentTask, int maxConcurrentTasks) {
        this.s3Client = s3Client;
        this.date = date;
        this.links = links;
        this.currentConcurrentTask = currentConcurrentTask;
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    @Override
    public String name() {
        return "MultiLinkList";
    }

    @Override
    public String describe() {
        return "List videos from multiple links";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        String dateValue = this.date;
        List<String> linkNames = new ArrayList<>();
        if (this.links != null && !this.links.isEmpty()) {
            linkNames = Arrays.stream(this.links.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        if (dateValue == null || dateValue.isEmpty()) {
            throw new Exception("MultiLinkListStage: date is required");
        }
        if (linkNames.isEmpty()) {
            throw new Exception("MultiLinkListStage: links are required");
        }

        List<String> allVideos = new ArrayList<>();

        for (String linkName : linkNames) {
            linkName = linkName.trim();
            if (linkName.isEmpty()) {
                continue;
            }

            // 获取链路配置
            LinkConfig linkConfig = getLinkConfigByName(linkName);
            if (linkConfig == null) {
                log.warn("[MultiLinkList] 未找到链路配置: {}，跳过", linkName);
                continue;
            }

            log.info("[MultiLinkList] 处理链路: {}", linkName);
            log.info("[MultiLinkList] 链路配置: BucketName={}, PathList={}", 
                linkConfig.getBucketName(), linkConfig.getPathList());

            // 构建S3前缀
            if (linkConfig.getPathList() == null || linkConfig.getPathList().isEmpty()) {
                log.warn("[MultiLinkList] 链路 {} 的 path_list 为空，跳过", linkName);
                continue;
            }
            String pathPrefix = linkConfig.getPathList().get(0);

            // 解析日期
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(dateValue, DateTimeFormatter.ISO_DATE);
            } catch (Exception e) {
                throw new Exception("日期格式错误: " + dateValue, e);
            }

            String year = String.valueOf(parsedDate.getYear());
            String formatMonth = String.format("%02d", parsedDate.getMonthValue());
            String day = String.format("%02d", parsedDate.getDayOfMonth());

            // 替换模板变量
            pathPrefix = pathPrefix.replace("{year}", year);
            pathPrefix = pathPrefix.replace("{month}", formatMonth);
            pathPrefix = pathPrefix.replace("{day}", day);

            // 构建S3前缀
            String prefix = pathPrefix;
            if (!prefix.endsWith("/")) {
                prefix += "/";
            }

            // 复用ListStage获取视频
            ListStage listStage = new ListStage(s3Client, linkConfig.getBucketName(), prefix);

            // 创建临时 Context 用于 ListStage
            PipelineContext tempCtx = new VideoFrameExtractionContext();
            tempCtx.setLinkName(linkName);
            try {
                listStage.process(tempCtx).get();
            } catch (Exception e) {
                log.warn("[MultiLinkList] 链路 {} 列举失败: {}，跳过", linkName, e.getMessage());
                continue;
            }

            // 从临时 Context 获取视频列表
            List<String> videos = tempCtx.getVideoKeys();
            allVideos.addAll(videos);
        }

        // 去重并排序
        List<String> result = deduplicateAndSortVideos(allVideos);
        log.info("[MultiLinkList] 多链路汇总: 共 {} 个视频", result.size());

        // 使用索引均匀分布分配视频到当前任务
        List<String> assignedVideos = distributeVideosByIndex(
            result, currentConcurrentTask, linkNames, maxConcurrentTasks);
        
        if (!assignedVideos.isEmpty()) {
            log.info("[MultiLinkList] 索引均匀分布后，当前任务({})应处理: {} 个视频，视频列表:\n{}", 
                currentConcurrentTask, assignedVideos.size(), String.join("\n", assignedVideos));
        } else {
            log.info("[MultiLinkList] 索引均匀分布后，当前任务({})应处理: {} 个视频", 
                currentConcurrentTask, assignedVideos.size());
        }

        // 将结果写入 Context
        pipelineCtx.setVideoKeys(assignedVideos);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 使用索引均匀分布算法将视频分配到不同的并发任务中
     */
    private List<String> distributeVideosByIndex(
            List<String> videos, int currentTask, List<String> linkNames, int maxConcurrentTasksFromArgs) {
        
        if (videos.isEmpty()) {
            return videos;
        }

        // 获取最大并发配置：优先使用命令行参数，否则使用配置文件中的值
        int maxConcurrentTasks = 1;
        if (maxConcurrentTasksFromArgs > 0) {
            maxConcurrentTasks = maxConcurrentTasksFromArgs;
        } else {
            // 从配置文件中获取所有链路的最大并发配置，取最大值
            for (String linkName : linkNames) {
                linkName = linkName.trim();
                if (linkName.isEmpty()) {
                    continue;
                }
                if (configService != null) {
                    VideoFrameExtractionConfig config = configService.getVideoFrameExtractionConfig(linkName);
                    if (config != null && config.getMaxConcurrentTasks() > 0) {
                        if (config.getMaxConcurrentTasks() > maxConcurrentTasks) {
                            maxConcurrentTasks = config.getMaxConcurrentTasks();
                        }
                    }
                }
            }
        }

        // 打印最大并发和当前并发信息
        int currentConcurrent = currentTask + 1;
        String source = maxConcurrentTasksFromArgs > 0 ? "命令行参数" : "配置文件";
        log.info("[MultiLinkList] 并发配置: 最大并发={} ({}), 当前并发={} (任务索引={})", 
            maxConcurrentTasks, source, currentConcurrent, currentTask);

        // 当当前并发和最大并发都是1时，处理所有视频
        if (currentTask == 0 && maxConcurrentTasks == 1) {
            log.info("[MultiLinkList] 当前并发和最大并发都是1，处理所有视频");
            return videos;
        }

        // 验证当前任务索引的有效性
        if (currentTask < 0 || currentTask >= maxConcurrentTasks) {
            log.warn("[MultiLinkList] 当前任务索引 {} 超出范围 [0, {})，返回空列表", 
                currentTask, maxConcurrentTasks);
            return new ArrayList<>();
        }

        // 使用索引均匀分布分配视频
        List<String> assignedVideos = new ArrayList<>();
        for (int i = 0; i < videos.size(); i++) {
            String video = videos.get(i);
            // 使用索引取模，轮询分配给不同任务
            int taskSlot = i % maxConcurrentTasks;
            // 如果分配给当前任务，则加入列表
            if (taskSlot == currentTask) {
                assignedVideos.add(video);
            }
        }

        // 记录分配统计信息
        if (!videos.isEmpty()) {
            double assignedRatio = (double) assignedVideos.size() / videos.size();
            double expectedRatio = 1.0 / maxConcurrentTasks;
            log.info("[MultiLinkList] 索引均匀分布统计: 总视频数={}, 当前任务分配数={}, 实际比例={:.2f}%, 期望比例={:.2f}%",
                videos.size(), assignedVideos.size(), assignedRatio * 100, expectedRatio * 100);
        }

        return assignedVideos;
    }

    /**
     * 根据链路名称获取配置
     */
    private LinkConfig getLinkConfigByName(String linkName) {
        if (configService == null) {
            // 默认配置
            return new LinkConfig("sj-ar", 
                Collections.singletonList("OCR_B_" + linkName.toUpperCase()), linkName);
        }

        VideoFrameExtractionConfig config = configService.getVideoFrameExtractionConfig(linkName);
        if (config == null) {
            // 默认配置
            return new LinkConfig("sj-ar", 
                Collections.singletonList("OCR_B_" + linkName.toUpperCase()), linkName);
        }

        // TODO: 从基础配置中获取存储名称对应的bucket
        String bucketName = "sj-ar"; // 默认值
        if (config.getInput() != null && config.getInput().getStorage() != null) {
            // 需要从BaseConfig中获取，这里暂时使用默认值
            bucketName = config.getInput().getStorage();
        }

        return new LinkConfig(bucketName, config.getInput().getPathList(), linkName);
    }

    /**
     * 去重并排序视频列表
     */
    private List<String> deduplicateAndSortVideos(List<String> videos) {
        Set<String> videoSet = new LinkedHashSet<>(videos);
        List<String> uniqueVideos = new ArrayList<>(videoSet);
        Collections.sort(uniqueVideos);
        return uniqueVideos;
    }

    /**
     * 链路配置
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class LinkConfig {
        private String bucketName;
        private List<String> pathList;
        private String linkName;
    }
}

