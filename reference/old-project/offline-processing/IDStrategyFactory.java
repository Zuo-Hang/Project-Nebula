package com.wuxiansheng.shieldarch.marsdata.offline.text;

/**
 * ID生成策略工厂
 * 对应 Go 版本的 text.NewIDStrategy
 */
public class IDStrategyFactory {

    /**
     * 创建ID生成策略，未知名称时回退到 RegStrategy
     * 
     * @param name 策略名称（如 "RegStrategy", "OrderListStrategy"）
     * @return ID生成策略实例
     */
    public static IDStrategy newIDStrategy(String name) {
        if (name == null || name.trim().isEmpty() || "RegStrategy".equals(name.trim())) {
            return new RegStrategy();
        }
        
        if ("OrderListStrategy".equals(name.trim())) {
            return new OrderListStrategy();
        }
        
        // 默认使用 RegStrategy
        return new RegStrategy();
    }
}

