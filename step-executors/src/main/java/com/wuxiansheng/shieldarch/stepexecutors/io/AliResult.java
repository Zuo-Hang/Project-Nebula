package com.wuxiansheng.shieldarch.stepexecutors.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR识别结果
 */
@Data
public class AliResult {
    
    @JsonProperty("ocrData")
    private List<String> ocrData = new ArrayList<>();
    
    @JsonProperty("ocrLocations")
    private List<AliPoint> ocrLocations = new ArrayList<>();
}

