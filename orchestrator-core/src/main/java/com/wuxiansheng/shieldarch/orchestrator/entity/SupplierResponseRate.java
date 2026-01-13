package com.wuxiansheng.shieldarch.orchestrator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商应答概率实体类
 */
@Data
@TableName("supplier_response_rate")
public class SupplierResponseRate {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 供应商名称
     */
    private String partnerName;
    
    /**
     * 应答概率（o_supplier_rate）
     */
    private BigDecimal responseRate;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted;
}

