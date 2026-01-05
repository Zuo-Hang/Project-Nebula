package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.mq.Producer;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationResult;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationSummary;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.S3UploadInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MQ处理阶段
 * 对应 Go 版本的 stages.MQStage
 */
@Slf4j
public class MQStage implements PipelineStage {

    @Autowired(required = false)
    private Producer producer;

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mq.producer.ocr-video-capture-topic:ocr_video_capture}")
    private String ocrVideoCaptureTopic;

    private final boolean skipMQ;

    public MQStage() {
        this(false);
    }

    public MQStage(boolean skipMQ) {
        this.skipMQ = skipMQ;
    }

    @Override
    public String name() {
        return "MQ";
    }

    @Override
    public String describe() {
        return "Build JSON result and send MQ message";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        String videoKey = pipelineCtx.getVideoKey();
        if (videoKey == null || videoKey.trim().isEmpty()) {
            throw new Exception("MQStage: VideoKey is required in context");
        }

        String linkName = pipelineCtx.getLinkName();
        if (linkName == null || linkName.trim().isEmpty()) {
            throw new Exception("MQStage: LinkName is required in context");
        }

        // TODO: 获取视频元数据（需要实现VideoMetadataStage）
        // 这里先使用占位数据
        StageVideoMetadata meta = new StageVideoMetadata();
        meta.setID(extractVideoID(videoKey));
        meta.setVideoURL(buildVideoURL(videoKey));
        meta.setCityName("");
        meta.setSupplierName("");
        meta.setDriverName("");
        meta.setFileCityName("");
        meta.setCityIllegal(false);

        VideoFrameExtractionConfig config = configService != null 
            ? configService.getVideoFrameExtractionConfig(linkName) 
            : null;
        if (config == null) {
            throw new Exception("链路配置未找到: " + linkName);
        }

        Object classificationValue = pipelineCtx.getClassification();
        ClassificationSummary classification = null;
        if (classificationValue instanceof ClassificationSummary) {
            classification = (ClassificationSummary) classificationValue;
        }

        Object uploadInfoValue = pipelineCtx.getS3UploadInfo();
        S3UploadInfo uploadInfo = null;
        if (uploadInfoValue instanceof S3UploadInfo) {
            uploadInfo = (S3UploadInfo) uploadInfoValue;
        }

        // 收集图片类型
        Map<String, List<String>> typeMapExact = new HashMap<>();
        Map<String, List<String>> typeMapBase = new HashMap<>();
        if (classification != null) {
            collectImageTypes(classification, typeMapExact, typeMapBase);
        }

        // 获取保留的图片路径
        List<String> imagePaths = new ArrayList<>(pipelineCtx.getKeptImagePaths());
        Collections.sort(imagePaths);

        List<MQImageItem> images = new ArrayList<>();
        for (String imgPath : imagePaths) {
            String base = new File(imgPath).getName();

            List<String> types = new ArrayList<>(typeMapExact.getOrDefault(imgPath, Collections.emptyList()));
            if (types.isEmpty()) {
                types = new ArrayList<>(typeMapBase.getOrDefault(base, Collections.emptyList()));
            }
            types = types.stream().distinct().sorted().collect(Collectors.toList());

            int index = extractIndex(base);
            String imageURL = buildImageURL(uploadInfo, base);

            MQImageItem item = new MQImageItem();
            item.setIndex(index);
            item.setURL(imageURL);
            item.setTypes(types);
            images.add(item);
        }

        String subLine = "";
        Object subLineValue = pipelineCtx.get(com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey.SUBMIT_DATE);
        if (subLineValue instanceof String) {
            subLine = ((String) subLineValue).trim();
        }

        MQPayload payload = new MQPayload();
        payload.setMeta(buildMQMeta(meta));
        payload.setImages(images);
        payload.setBusiness(buildBusinessLabel(linkName));
        payload.setSubmitDate(submitDate(pipelineCtx));
        if (!subLine.isEmpty()) {
            payload.setSubLine(subLine);
        }

        String payloadJson = objectMapper.writeValueAsString(payload);
        log.info("MQ payload: {}", payloadJson);

        if (skipMQ || "true".equalsIgnoreCase(System.getenv("SKIP_MQ"))) {
            log.warn("MQStage: 跳过 MQ 发送 (SkipMQ)");
            return CompletableFuture.completedFuture(null);
        }

        if (producer == null) {
            throw new Exception("MQStage: Producer 未配置");
        }

        if (ocrVideoCaptureTopic == null || ocrVideoCaptureTopic.trim().isEmpty()) {
            throw new Exception("MQStage: MQ Topic 未配置");
        }

        producer.send(ocrVideoCaptureTopic, payloadJson);
        log.info("MQStage: 已发送 MQ 消息到 topic={}", ocrVideoCaptureTopic);
        return CompletableFuture.completedFuture(null);
    }

    private void collectImageTypes(ClassificationSummary summary, 
                                  Map<String, List<String>> byPath,
                                  Map<String, List<String>> byBase) {
        if (summary == null || summary.getResults() == null) {
            return;
        }

        for (Map.Entry<String, List<ClassificationResult>> entry : summary.getResults().entrySet()) {
            String pageType = entry.getKey();
            for (ClassificationResult r : entry.getValue()) {
                if (r.getImagePath() == null || r.getImagePath().isEmpty()) {
                    continue;
                }
                byPath.computeIfAbsent(r.getImagePath(), k -> new ArrayList<>()).add(pageType);
                String base = new File(r.getImagePath()).getName();
                byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(pageType);
            }
        }
    }

    private int extractIndex(String filename) {
        String name = filename.substring(0, filename.lastIndexOf('.') > 0 
            ? filename.lastIndexOf('.') : filename.length());
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private String buildImageURL(S3UploadInfo uploadInfo, String filename) {
        // TODO: 实现真实的S3 URL构建逻辑
        if (uploadInfo != null && uploadInfo.getS3URLPrefix() != null) {
            return uploadInfo.getS3URLPrefix() + "/" + filename;
        }
        return "";
    }

    private String buildVideoURL(String videoKey) {
        // TODO: 实现真实的视频URL构建逻辑
        return videoKey;
    }

    private String extractVideoID(String videoKey) {
        // TODO: 从videoKey中提取ID
        return new File(videoKey).getName();
    }

    private MQMeta buildMQMeta(StageVideoMetadata meta) {
        MQMeta mqMeta = new MQMeta();
        mqMeta.setID(meta.getID());
        mqMeta.setVideoURL(meta.getVideoURL());
        mqMeta.setCityName(meta.getCityName());
        mqMeta.setSupplierName(meta.getSupplierName());
        mqMeta.setDriverName(meta.getDriverName());
        mqMeta.setFileCityName(meta.getFileCityName());
        mqMeta.setCityIllegal(meta.isCityIllegal());
        return mqMeta;
    }

    private String buildBusinessLabel(String linkName) {
        if (linkName == null || linkName.isEmpty()) {
            return "";
        }
        return "B-" + linkName.toUpperCase();
    }

    private String submitDate(PipelineContext pipelineCtx) {
        Object value = pipelineCtx.get(com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ContextKey.SUBMIT_DATE);
        if (value instanceof String) {
            String date = ((String) value).trim();
            if (!date.isEmpty()) {
                return date;
            }
        }
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }

    // 数据类
    @Data
    private static class MQPayload {
        @JsonProperty("meta")
        private MQMeta meta;

        @JsonProperty("images")
        private List<MQImageItem> images;

        @JsonProperty("business")
        private String business;

        @JsonProperty("submit_date")
        private String submitDate;

        @JsonProperty("sub_line")
        private String subLine;
    }

    @Data
    private static class MQMeta {
        @JsonProperty("id")
        private String ID;

        @JsonProperty("video_url")
        private String videoURL;

        @JsonProperty("city_name")
        private String cityName;

        @JsonProperty("supplier_name")
        private String supplierName;

        @JsonProperty("driver_name")
        private String driverName;

        @JsonProperty("file_city_name")
        private String fileCityName;

        @JsonProperty("city_illegal")
        private boolean cityIllegal;
    }

    @Data
    private static class MQImageItem {
        @JsonProperty("index")
        private int index;

        @JsonProperty("url")
        private String URL;

        @JsonProperty("types")
        private List<String> types;
    }

    @Data
    private static class StageVideoMetadata {
        private String ID;
        private String videoURL;
        private String cityName;
        private String supplierName;
        private String driverName;
        private String fileCityName;
        private boolean cityIllegal;
    }
}

