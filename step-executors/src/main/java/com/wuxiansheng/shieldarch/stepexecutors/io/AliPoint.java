package com.wuxiansheng.shieldarch.stepexecutors.io;

import lombok.Data;

/**
 * 文本位置信息（x,y为左上角，w,h为宽高，c为置信度）
 */
@Data
public class AliPoint {
    
    /**
     * X坐标（左上角）
     */
    private Double x;
    
    /**
     * Y坐标（左上角）
     */
    private Double y;
    
    /**
     * 宽度
     */
    private Double w;
    
    /**
     * 高度
     */
    private Double h;
    
    /**
     * 文本内容
     */
    private String text;
    
    /**
     * 置信度
     */
    private Double c;
}

