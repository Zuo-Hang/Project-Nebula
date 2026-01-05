package com.wuxiansheng.shieldarch.marsdata.pipeline.storage;

import com.wuxiansheng.shieldarch.marsdata.io.AliResult;
import com.wuxiansheng.shieldarch.marsdata.io.OcrClient;
import com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces.OCRPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCRPort适配器
 * 将OcrClient适配为OCRPort接口
 */
public class OcrPortAdapter implements OCRPort {

    private final OcrClient ocrClient;

    public OcrPortAdapter(OcrClient ocrClient) {
        this.ocrClient = ocrClient;
    }

    @Override
    public Map<String, String> recognizeBatch(List<String> imagePaths) throws Exception {
        if (ocrClient == null) {
            throw new Exception("OcrClient not configured");
        }
        
        // 调用OcrClient的批量识别方法
        Map<String, AliResult> results = ocrClient.recognizeFilesOnce(imagePaths);
        
        // 将AliResult转换为String（OCR文本）
        Map<String, String> textByImage = new HashMap<>();
        for (Map.Entry<String, AliResult> entry : results.entrySet()) {
            AliResult result = entry.getValue();
            String text = "";
            if (result != null && result.getOcrData() != null && !result.getOcrData().isEmpty()) {
                text = String.join("\n", result.getOcrData());
            }
            textByImage.put(entry.getKey(), text);
        }
        
        return textByImage;
    }
}

