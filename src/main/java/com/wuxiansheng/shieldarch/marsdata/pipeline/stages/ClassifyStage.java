package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.offline.image.ClassificationMatch;
import com.wuxiansheng.shieldarch.marsdata.offline.image.ImageClassifier;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationResult;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationSummary;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 图片分类阶段
 * 对应 Go 版本的 stages.ClassifyStage
 */
@Slf4j
public class ClassifyStage implements PipelineStage {

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    @Override
    public String name() {
        return "Classify";
    }

    @Override
    public String describe() {
        return "Classify images by business rules";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        List<String> imagePaths = pipelineCtx.getImagePaths();
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new Exception("ClassifyStage: ImagePaths is required in context");
        }

        Map<String, String> textByImage = pipelineCtx.getOCRTextByImage();
        if (textByImage == null || textByImage.isEmpty()) {
            throw new Exception("ClassifyStage: OCRTextByImage is required in context");
        }

        String linkName = pipelineCtx.getLinkName();
        if (linkName == null || linkName.isEmpty()) {
            throw new Exception("ClassifyStage: LinkName is required in context");
        }

        ImageClassifier classifier = new ImageClassifier(linkName, configService);

        long startTime = System.currentTimeMillis();
        ClassificationSummary summary = new ClassificationSummary();
        summary.setTypeCounts(new HashMap<>());
        summary.setResults(new HashMap<>());

        for (String imagePath : imagePaths) {
            String ocrText = textByImage.get(imagePath);
            if (ocrText == null || ocrText.trim().isEmpty()) {
                // 如果没有OCR结果，记录为unknown类型
                ClassificationResult unknownResult = new ClassificationResult();
                unknownResult.setImagePath(imagePath);
                unknownResult.setPageType("unknown");
                unknownResult.setError(new Exception("未找到OCR识别结果"));
                summary.getTypeCounts().put("unknown", 
                    summary.getTypeCounts().getOrDefault("unknown", 0) + 1);
                summary.getResults().computeIfAbsent("unknown", k -> new ArrayList<>())
                    .add(unknownResult);
                continue;
            }

            List<ClassificationMatch> matches = classifier.classifyByText(ocrText);
            if (matches == null || matches.isEmpty()) {
                matches = Collections.singletonList(createUnknownMatch());
            }

            for (ClassificationMatch match : matches) {
                String pageType = match.getPageType();
                List<String> matchedKeywords = match.getMatchedKeywords();

                ClassificationResult result = new ClassificationResult();
                result.setImagePath(imagePath);
                result.setPageType(pageType);
                result.setOcrText(ocrText);
                result.setMatchedKeywords(matchedKeywords != null ? matchedKeywords : Collections.emptyList());
                result.setConfidence(0.8);
                result.setProcessTime(System.currentTimeMillis() - startTime);

                summary.getTypeCounts().put(pageType, 
                    summary.getTypeCounts().getOrDefault(pageType, 0) + 1);
                summary.getResults().computeIfAbsent(pageType, k -> new ArrayList<>())
                    .add(result);
            }
        }

        log.info("ClassifyStage 完成，共 {} 个页面类型", summary.getTypeCounts().size());
        pipelineCtx.setClassification(summary);
        return CompletableFuture.completedFuture(null);
    }

    private ClassificationMatch createUnknownMatch() {
        ClassificationMatch match = new ClassificationMatch();
        match.setPageType("unknown");
        match.setMatchedKeywords(Collections.emptyList());
        match.setMatchCount(0);
        return match;
    }
}

