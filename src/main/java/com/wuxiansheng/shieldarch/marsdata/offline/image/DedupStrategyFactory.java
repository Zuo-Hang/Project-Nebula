package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * 去重策略工厂
 * 对应 Go 版本的 image.CreateDedupStrategyFromRuleName
 */
@Slf4j
public class DedupStrategyFactory {

    /**
     * 根据规则名称创建去重策略
     * 
     * @param ruleName 规则名称（如 "General_dedup", "SlidingWindow_dedup", "CoverageMinSet_dedup"）
     * @param params 规则参数
     * @return 去重策略实例
     */
    public static DedupStrategy createDedupStrategyFromRuleName(String ruleName, Map<String, Object> params) {
        if (ruleName == null || ruleName.trim().isEmpty()) {
            ruleName = "General_dedup";
        }

        String normalizedRuleName = ruleName.trim();
        
        switch (normalizedRuleName) {
            case "General_dedup":
            case "Global_dedup":
                return new GlobalIDDedup();
                
            case "SlidingWindow_dedup":
                int windowSize = 100; // 默认窗口大小
                if (params != null) {
                    Object sizeObj = params.get("window_size");
                    if (sizeObj instanceof Number) {
                        windowSize = ((Number) sizeObj).intValue();
                    }
                }
                if (windowSize <= 0) {
                    windowSize = 100;
                }
                return new SlidingWindowIDDedup(windowSize);
                
            case "CoverageMinSet_dedup":
                return new CoverageMinSetDedup();
                
            default:
                log.warn("未知的去重规则名称: {}, 使用默认的 Global_dedup", ruleName);
                return new GlobalIDDedup();
        }
    }
}

