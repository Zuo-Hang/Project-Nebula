package com.wuxiansheng.shieldarch.marsdata.business.xlprice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 小拉规则数据
 */
@Data
public class XLRuleData {
    
    @JsonProperty("ruleId")
    private Integer ruleID;
    
    @JsonProperty("ruleDetailId")
    private Integer ruleDetailID;
    
    @JsonProperty("partitionId")
    private Integer partitionID;
    
    @JsonProperty("ruleType")
    private Integer ruleType;
    
    @JsonProperty("startDistance")
    private Double startDistance;
    
    @JsonProperty("startTimeLen")
    private Double startTimeLen;
    
    @JsonProperty("startPriceRule")
    private XLPriceRule startPriceRule;
    
    @JsonProperty("exceedDistanceRule")
    private XLPriceRule exceedDistanceRule;
    
    @JsonProperty("exceedTimeLenRule")
    private XLPriceRule exceedTimeLenRule;
    
    @JsonProperty("longDistanceRule")
    private LongDistanceRule longDistanceRule;
    
    /**
     * 重构单位：m -> km，分 -> 元
     */
    public void refactorUnit() {
        if (startDistance != null) {
            startDistance = startDistance / 1000;
        }
        if (startPriceRule != null) {
            startPriceRule.refactorUnit();
        }
        if (exceedDistanceRule != null) {
            exceedDistanceRule.refactorUnit();
        }
        if (exceedTimeLenRule != null) {
            exceedTimeLenRule.refactorUnit();
        }
        if (longDistanceRule != null) {
            longDistanceRule.refactorDisUnit();
        }
    }
}

