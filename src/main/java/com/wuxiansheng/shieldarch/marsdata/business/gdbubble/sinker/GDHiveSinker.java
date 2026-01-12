package com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleInput;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleReasonResult;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.ReasonSupplierResult;
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
 * 高德冒泡Hive Sinker
 */
@Slf4j
@Component
public class GDHiveSinker extends HiveSinker {
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof GDBubbleBusiness)) {
            return;
        }
        
        GDBubbleBusiness gb = (GDBubbleBusiness) business;
        
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
    private List<HiveRaw> newHiveRaws(GDBubbleBusiness gb) {
        List<HiveRaw> res = new ArrayList<>();
        
        GDBubbleInput input = gb.getInput();
        GDBubbleReasonResult reasonResult = gb.getReasonResult();
        
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
            raw.setClientTime(input.getClientTime());
            raw.setIsStation(input.getIsStation() != null ? input.getIsStation() : false);
            raw.setStationName(input.getStationName());
            raw.setRidePlatformName(input.getRidePlatformName());
            raw.setRidePlatformId(input.getRidePlatformId());
            raw.setCityCheck(gb.getCityCheck());
            raw.setStartLNG(gb.getStartLNG());
            raw.setStartLAT(gb.getStartLAT());
            
            raw.setEstimatedDistance(reasonResult.getEstimatedDistance());
            raw.setEstimatedTime(reasonResult.getEstimatedTime());
            raw.setStartPoint(reasonResult.getStartPoint());
            raw.setEndPoint(reasonResult.getEndPoint());
            raw.setCreationTime(reasonResult.getCreationTime());
            raw.setSupplier(supplierInfo.getSupplier());
            raw.setEstPrice(supplierInfo.getEstPrice());
            raw.setCapPrice(supplierInfo.getCapPrice());
            raw.setPriceRange(supplierInfo.getPriceRange());
            raw.setDiscountType(supplierInfo.getDiscountType());
            raw.setDiscountAmount(supplierInfo.getDiscountAmount());
            raw.setPriceType(supplierInfo.getPriceType());
            raw.setOtherPrice(supplierInfo.getOtherPrice());
            raw.setCarType(supplierInfo.getCarType());
            
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
        private Long clientTime;
        private Boolean isStation;
        private String stationName;
        private String ridePlatformName;
        private Integer ridePlatformId;
        private String cityCheck;
        private String startLNG;
        private String startLAT;
        private Double estimatedDistance;
        private Double estimatedTime;
        private String startPoint;
        private String endPoint;
        private String creationTime;
        private String supplier;
        private Double estPrice;
        private Double capPrice;
        private List<Double> priceRange;
        private String discountType;
        private Double discountAmount;
        private String priceType;
        private Double otherPrice;
        private String carType;
    }
}

