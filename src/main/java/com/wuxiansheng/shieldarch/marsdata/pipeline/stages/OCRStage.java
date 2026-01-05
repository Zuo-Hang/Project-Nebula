package com.wuxiansheng.shieldarch.marsdata.pipeline.stages;

import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.OCRPort;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineContext;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * OCR识别阶段
 * 对应 Go 版本的 stages.OCRStage
 */
@Slf4j
public class OCRStage implements PipelineStage {

    private final OCRPort ocr;

    public OCRStage(OCRPort ocr) {
        this.ocr = ocr;
    }

    @Override
    public String name() {
        return "OCR";
    }

    @Override
    public String describe() {
        return "Batch OCR recognition";
    }

    @Override
    public CompletableFuture<Void> process(PipelineContext pipelineCtx) throws Exception {
        // 从 Context 获取图片路径
        List<String> imagePaths = pipelineCtx.getImagePaths();
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new Exception("OCRStage: ImagePaths is required in context (当前为空)");
        }

        // 检查 OCR adapter 是否注入
        if (ocr == null) {
            throw new Exception("OCRStage: OCR adapter is required (use dependency injection)");
        }

        log.info("批量OCR识别开始，共 {} 张图片", imagePaths.size());

        // 调用 OCR adapter 进行批量识别
        Map<String, String> textByImage = ocr.recognizeBatch(imagePaths);
        if (textByImage == null) {
            textByImage = new HashMap<>();
        }

        // 验证识别结果
        int successCount = 0;
        int emptyCount = 0;
        List<String> emptyPaths = new ArrayList<>();

        for (Map.Entry<String, String> entry : textByImage.entrySet()) {
            String text = entry.getValue();
            if (text != null && !text.trim().isEmpty()) {
                successCount++;
            } else {
                emptyCount++;
                emptyPaths.add(entry.getKey());
            }
        }

        if (!emptyPaths.isEmpty()) {
            log.warn("图片识别结果为空: {}", String.join(", ", emptyPaths));
        }

        log.info("OCR识别完成: 成功 {} 张，空结果 {} 张，总计 {} 张", 
            successCount, emptyCount, textByImage.size());

        // 将结果写入 Context
        pipelineCtx.setOCRTextByImage(textByImage);
        return CompletableFuture.completedFuture(null);
    }
}

