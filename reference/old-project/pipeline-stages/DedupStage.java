package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfig;
import com.wuxiansheng.shieldarch.marsdata.config.VideoFrameExtractionConfigService;
import com.wuxiansheng.shieldarch.marsdata.offline.image.*;
import com.wuxiansheng.shieldarch.marsdata.offline.text.IDStrategy;
import com.wuxiansheng.shieldarch.marsdata.offline.text.IDStrategyFactory;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationResult;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.ClassificationSummary;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 图片去重阶段
 * 对应 Go 版本的 stages.DedupStage
 */
@Slf4j
public class DedupStage implements PipelineStage {

    @Autowired(required = false)
    private VideoFrameExtractionConfigService configService;

    @Override
    public String name() {
        return "Dedup";
    }

    @Override
    public String describe() {
        return "Deduplicate images by page type and order";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        String linkName = pipelineCtx.getLinkName();
        if (linkName == null || linkName.isEmpty()) {
            throw new Exception("DedupStage: pipelineCtx 中缺少 LinkName");
        }

        Object classificationValue = pipelineCtx.getClassification();
        if (classificationValue == null) {
            throw new Exception("DedupStage: 未找到分类结果，请确保前置阶段已设置 Classification");
        }

        ClassificationSummary classification;
        if (classificationValue instanceof ClassificationSummary) {
            classification = (ClassificationSummary) classificationValue;
        } else {
            throw new Exception("DedupStage: 分类结果类型不匹配，期待 ClassificationSummary");
        }

        VideoFrameExtractionConfig config = configService != null 
            ? configService.getVideoFrameExtractionConfig(linkName) 
            : null;
        if (config == null) {
            throw new Exception("DedupStage: 未加载链路配置，link=" + linkName);
        }

        Map<String, String> textByImage = pipelineCtx.getOCRTextByImage();
        if (textByImage == null) {
            textByImage = Collections.emptyMap();
            log.warn("DedupStage: OCRTextByImage 为空，可能无法为部分页面生成唯一ID");
        }

        // 构建页面配置映射
        Map<String, VideoFrameExtractionConfig.PageConfig> pageConfigMap = new HashMap<>();
        if (config.getPages() != null) {
            for (VideoFrameExtractionConfig.PageConfig pageCfg : config.getPages()) {
                pageConfigMap.put(pageCfg.getPageType(), pageCfg);
            }
        }

        List<String> keptList = new ArrayList<>();
        Map<String, PageDedupSummary> pageSummaries = new HashMap<>();

        int totalBefore = 0;
        int totalRemoved = 0;

        // 按页面类型处理
        for (Map.Entry<String, List<ClassificationResult>> entry : classification.getResults().entrySet()) {
            String pageType = entry.getKey();
            List<ClassificationResult> results = entry.getValue();
            totalBefore += results.size();

            VideoFrameExtractionConfig.PageConfig cfg = pageConfigMap.get(pageType);
            if (cfg == null) {
                log.warn("DedupStage: 未找到页面类型的配置，跳过去重，page_type={}", pageType);
                totalRemoved += results.size();
                continue;
            }

            // 构建去重输入
            List<PageDedupInput> inputs = buildDedupInputs(results, cfg, pageType, textByImage);
            if (inputs.isEmpty()) {
                continue;
            }

            // 解析去重策略
            String strategyName = resolveStrategy(cfg);
            Map<String, Object> params = resolveParams(cfg);
            DedupStrategy strategy = DedupStrategyFactory.createDedupStrategyFromRuleName(strategyName, params);
            if (strategy == null) {
                log.warn("DedupStage: 创建去重策略失败，page_type={}, strategy={}", pageType, strategyName);
                continue;
            }

            // 执行去重
            PageDedupSummary summary = strategy.deduplicate(inputs);
            pageSummaries.put(pageType, summary);
            totalRemoved += summary.getRemovedPages();

            log.info("DedupStage: [{}] 去重后保留 {} 张，移除 {} 张", 
                pageType, summary.getKeptPages(), summary.getRemovedPages());

            // 收集保留的图片
            Set<String> keptSet = new HashSet<>();
            for (PageDedupResult result : summary.getResults()) {
                if ("kept".equals(result.getAction())) {
                    String imagePath = result.getImagePath();
                    if (!keptSet.contains(imagePath)) {
                        keptSet.add(imagePath);
                        keptList.add(imagePath);
                    }
                }
            }
        }

        Collections.sort(keptList);
        pipelineCtx.setKeptImagePaths(keptList);
        pipelineCtx.setPageTypeDedup(pageSummaries);
        pipelineCtx.setClassification(classification);

        if (totalBefore > 0) {
            int totalKept = totalBefore - totalRemoved;
            if (totalKept < 0) {
                totalKept = 0;
            }
            double rate = (double) totalRemoved / totalBefore * 100;
            log.info("DedupStage 完成，保留 {} 张，移除 {} 张，去重率 {:.1f}%", 
                totalKept, totalRemoved, rate);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 构建去重输入
     */
    private List<PageDedupInput> buildDedupInputs(
            List<ClassificationResult> results,
            VideoFrameExtractionConfig.PageConfig cfg,
            String pageType,
            Map<String, String> textByImage) {
        
        List<PageDedupInput> inputs = new ArrayList<>();
        for (ClassificationResult res : results) {
            String ocrText = res.getOcrText();
            if (ocrText == null || ocrText.trim().isEmpty()) {
                if (textByImage != null) {
                    ocrText = textByImage.get(res.getImagePath());
                }
            }
            if (ocrText == null || ocrText.trim().isEmpty()) {
                log.warn("DedupStage: 去重时仍缺少 OCR 文本，page_type={}, image={}", 
                    pageType, res.getImagePath());
                continue;
            }

            // 生成ID
            List<String> ids = generateIDs(cfg, pageType, res.getImagePath(), ocrText);
            List<String> cleanIDs = ids.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            PageDedupInput input = new PageDedupInput();
            input.setImagePath(res.getImagePath());
            input.setUniqueIDs(cleanIDs);
            inputs.add(input);
        }
        return inputs;
    }

    /**
     * 生成ID
     */
    private List<String> generateIDs(
            VideoFrameExtractionConfig.PageConfig cfg,
            String pageType,
            String imagePath,
            String ocrText) {
        
        VideoFrameExtractionConfig.PageDedupConfig dedupCfg = cfg.getDedup();
        if (dedupCfg == null) {
            return Collections.emptyList();
        }

        VideoFrameExtractionConfig.PageDedupIDConfig idCfg = dedupCfg.getId();
        String strategyName = "RegStrategy";
        if (idCfg != null && idCfg.getStrategyClass() != null && !idCfg.getStrategyClass().trim().isEmpty()) {
            strategyName = idCfg.getStrategyClass().trim();
        }

        List<String> patterns = new ArrayList<>();
        if (idCfg != null && idCfg.getMach() != null && !idCfg.getMach().trim().isEmpty()) {
            patterns.add(idCfg.getMach().trim());
        }
        if (dedupCfg.getParam() != null && dedupCfg.getParam().containsKey("mach")) {
            String mach = dedupCfg.getParam().get("mach");
            if (mach != null && !mach.trim().isEmpty()) {
                patterns.add(mach.trim());
            }
        }

        IDStrategy strategy = IDStrategyFactory.newIDStrategy(strategyName);
        try {
            return strategy.generateIDs(ocrText, patterns);
        } catch (Exception e) {
            log.warn("生成ID失败: pageType={}, error={}", pageType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析去重策略名称
     */
    private String resolveStrategy(VideoFrameExtractionConfig.PageConfig cfg) {
        VideoFrameExtractionConfig.PageDedupConfig dedup = cfg.getDedup();
        if (dedup == null) {
            return "General_dedup";
        }

        String strategyClass = dedup.getStrategyClass();
        if (strategyClass != null && !strategyClass.trim().isEmpty()) {
            return strategyClass.trim();
        }

        String rule = dedup.getRule();
        if (rule != null && !rule.trim().isEmpty()) {
            return rule.trim();
        }

        return "General_dedup";
    }

    /**
     * 解析参数
     */
    private Map<String, Object> resolveParams(VideoFrameExtractionConfig.PageConfig cfg) {
        Map<String, Object> params = new HashMap<>();
        VideoFrameExtractionConfig.PageDedupConfig dedup = cfg.getDedup();
        if (dedup == null) {
            return params;
        }

        if (dedup.getParams() != null) {
            params.putAll(dedup.getParams());
        }
        if (dedup.getParam() != null) {
            for (Map.Entry<String, String> entry : dedup.getParam().entrySet()) {
                params.put(entry.getKey(), entry.getValue());
            }
        }

        return params;
    }
}

