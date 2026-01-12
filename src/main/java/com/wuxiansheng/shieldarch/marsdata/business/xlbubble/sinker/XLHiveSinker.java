package com.wuxiansheng.shieldarch.marsdata.business.xlbubble.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleInput;
import com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleReasonResult;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.sinker.HiveSinker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 小拉冒泡Hive Sinker
 */
@Slf4j
@Component
public class XLHiveSinker extends HiveSinker {
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof XLBubbleBusiness)) {
            return;
        }
        
        XLBubbleBusiness gb = (XLBubbleBusiness) business;
        
        if (gb.getReasonResult() == null || gb.getReasonResult().getSuppliersInfo() == null) {
            return;
        }
        
        List<HiveRaw> hiveRaws = newHiveRaws(gb);
        for (HiveRaw raw : hiveRaws) {
            printToHive(raw, business.getName(), gb.getMsgTimestamp());
        }
    }
    
    /**
     * 创建Hive行数据列表
     */
    private List<HiveRaw> newHiveRaws(XLBubbleBusiness gb) {
        List<HiveRaw> res = new ArrayList<>();
        
        XLBubbleInput input = gb.getInput();
        XLBubbleReasonResult reasonResult = gb.getReasonResult();
        
        for (ReasonSupplierResult supplierInfo : reasonResult.getSuppliersInfo()) {
            HiveRaw raw = new HiveRaw();
            raw.setEstimateId(input.getEstimateId());
            raw.setActivityName(input.getActivityName());
            
            // 将图片URL列表转换为逗号分隔的字符串
            StringJoiner sj = new StringJoiner(",");
            if (input.getBubbleImageUrls() != null) {
                input.getBubbleImageUrls().forEach(sj::add);
            }
            raw.setBubbleImageUrls(sj.toString());
            
            raw.setCityName(input.getCityName());
            raw.setCityId(input.getCityId());
            raw.setTimeRange(input.getTimeRange());
            raw.setDisRange(input.getDisRange());
            raw.setDistrictType(input.getDistrictType());
            raw.setDistrictName(input.getDistrictName());
            raw.setSubmitName(input.getSubmitName());
            raw.setSubmitTime(input.getSubmitTime());
            raw.setSubmitTimestampMs(input.getSubmitTimestampMs());
            raw.setCityCheck(gb.getCityCheck());
            
            raw.setEstimatedDistance(reasonResult.getEstimatedDistance());
            raw.setEstimatedTime(reasonResult.getEstimatedTime());
            raw.setStartPoint(reasonResult.getStartPoint());
            raw.setEndPoint(reasonResult.getEndPoint());
            raw.setBubbleTime(reasonResult.getBubbleTime());
            raw.setSupplier(supplierInfo.getSupplier());
            raw.setPrice(supplierInfo.getPrice());
            raw.setDiscountAmount(supplierInfo.getDiscountAmount());
            raw.setPriceType(supplierInfo.getPriceType());
            
            res.add(raw);
        }
        
        return res;
    }
    
    /**
     * Hive行数据
     */
    @Data
    private static class HiveRaw {
        private String estimateId;
        private String activityName;
        private String bubbleImageUrls;
        private String cityName;
        private Integer cityId;
        private String timeRange;
        private String disRange;
        private String districtType;
        private String districtName;
        private String submitName;
        private String submitTime;
        private Long submitTimestampMs;
        private String cityCheck;
        private Double estimatedDistance;
        private Double estimatedTime;
        private String startPoint;
        private String endPoint;
        private String bubbleTime;
        private String supplier;
        private Double price;
        private Double discountAmount;
        private String priceType;
    }
}

