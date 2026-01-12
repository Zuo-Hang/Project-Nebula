package com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasInput;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasPassengerDetail;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasPersonalHomepage;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.sinker.HiveSinker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 司机基础信息Hive Sinker
 */
@Slf4j
@Component
public class DriverBaseHiveSinker extends HiveSinker {
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof BSaasBusiness)) {
            return;
        }
        
        BSaasBusiness bs = (BSaasBusiness) business;
        if (bs.getReasonResult() == null) {
            return;
        }
        
        List<Map<String, Object>> records = buildDriverBaseRecords(bs);
        for (Map<String, Object> record : records) {
            printToHive(record, business.getName(), bs.getMsgTimestamp());
        }
    }
    
    /**
     * 构建司机基础记录
     */
    private List<Map<String, Object>> buildDriverBaseRecords(BSaasBusiness bs) {
        BSaasInput.Meta meta = bs.getInput() != null ? bs.getInput().getMeta() : null;
        BSaasPersonalHomepage personal = firstPersonalHomepage(bs.getReasonResult().getPersonalHomepage());
        String productType = firstProductType(bs.getReasonResult().getPassengerDetails());
        
        Map<String, DriverBaseAggregate> aggregates = new HashMap<>();
        
        // 处理历史统计数据
        if (bs.getReasonResult().getHistoricalStatistics() != null) {
            for (var stat : bs.getReasonResult().getHistoricalStatistics()) {
                String key = rangeKey(stat.getStartDate(), stat.getEndDate());
                DriverBaseAggregate agg = ensureAggregate(aggregates, key);
                agg.startDate = stat.getStartDate();
                agg.endDate = stat.getEndDate();
                agg.summaryOrderCount = stat.getSummaryOrderCount() != null ? stat.getSummaryOrderCount() : 0;
                agg.driverCancelCount = stat.getDriverCancelCount() != null ? stat.getDriverCancelCount() : 0;
                agg.onlineDuration = stat.getOnlineDuration() != null ? stat.getOnlineDuration() : 0.0;
                agg.peakOnlineDuration = stat.getPeakOnlineDuration() != null ? stat.getPeakOnlineDuration() : 0.0;
                agg.serveDuration = stat.getServeDuration() != null ? stat.getServeDuration() : 0.0;
                agg.incomeAmount = stat.getIncomeAmount() != null ? stat.getIncomeAmount() : 0.0;
                agg.rewardAmount = stat.getRewardAmount() != null ? stat.getRewardAmount() : 0.0;
                agg.sourceType = preferSource(agg.sourceType, stat.getSourceType());
                agg.okServerDayCount = 0;
                if (stat.getImageURL() != null && !stat.getImageURL().isEmpty()) {
                    agg.images.add(stat.getImageURL());
                }
            }
        }
        
        // 处理业绩交易数据
        if (bs.getReasonResult().getPerformanceTransactions() != null) {
            for (var perf : bs.getReasonResult().getPerformanceTransactions()) {
                String key = rangeKey(perf.getStartDate(), perf.getEndDate());
                DriverBaseAggregate agg = ensureAggregate(aggregates, key);
                agg.startDate = perf.getStartDate();
                agg.endDate = perf.getEndDate();
                agg.performanceOrderCount = perf.getPerformanceOrderCount() != null ? perf.getPerformanceOrderCount() : 0;
                if (perf.getSummaryOrderCount() != null && perf.getSummaryOrderCount() != 0) {
                    agg.summaryOrderCount = perf.getSummaryOrderCount();
                }
                if (perf.getOnlineDuration() != null && perf.getOnlineDuration() != 0) {
                    agg.onlineDuration = perf.getOnlineDuration();
                }
                if (perf.getServeDuration() != null && perf.getServeDuration() != 0) {
                    agg.serveDuration = perf.getServeDuration();
                }
                if (perf.getIncomeAmount() != null && perf.getIncomeAmount() != 0) {
                    agg.incomeAmount = perf.getIncomeAmount();
                }
                if (perf.getRewardAmount() != null && perf.getRewardAmount() != 0) {
                    agg.rewardAmount = perf.getRewardAmount();
                }
                agg.sourceType = preferSource(agg.sourceType, perf.getSourceType());
                if (perf.getImageURL() != null && !perf.getImageURL().isEmpty()) {
                    agg.images.add(perf.getImageURL());
                }
            }
        }
        
        // 添加个人主页图片
        if (personal != null && personal.getImageURL() != null && !personal.getImageURL().isEmpty()) {
            for (DriverBaseAggregate agg : aggregates.values()) {
                agg.images.add(personal.getImageURL());
            }
        }
        
        // 构建记录
        List<Map<String, Object>> baseRecords = new ArrayList<>();
        for (DriverBaseAggregate agg : aggregates.values()) {
            String images = joinImages(agg.images);
            Map<String, Object> record = new HashMap<>();
            record.put("images", images);
            record.put("estimate_id", meta != null ? meta.getId() : "");
            record.put("supplier_name", meta != null ? meta.getSupplierName() : "");
            record.put("driver_id", meta != null ? meta.getDriverName() : "");
            record.put("driver_last_name", "");
            record.put("car_type", personal != null ? personal.getCarType() : "");
            record.put("car_number", personal != null ? personal.getCarNumber() : "");
            record.put("product_type", productType);
            record.put("start_date", agg.startDate);
            record.put("end_date", agg.endDate);
            record.put("summary_order_count", agg.summaryOrderCount);
            record.put("driver_cancel_count", agg.driverCancelCount);
            record.put("online_duration", agg.onlineDuration);
            record.put("peak_online_duration", agg.peakOnlineDuration);
            record.put("ok_server_day_count", agg.okServerDayCount);
            record.put("serve_duration", agg.serveDuration);
            record.put("income_amount", agg.incomeAmount);
            record.put("reward_amount", agg.rewardAmount);
            record.put("performance_order_count", agg.performanceOrderCount);
            record.put("source_type", "VIDEO");
            record.put("city_name", meta != null ? meta.getCityName() : "");
            record.put("activity_name", "");
            record.put("city_illegal", boolToInt(meta != null && meta.getCityIllegal() != null && meta.getCityIllegal()));
            record.put("platform", bs.getInput() != null ? bs.getInput().getBusiness() : "");
            
            baseRecords.add(record);
        }
        
        return baseRecords;
    }
    
    /**
     * 聚合数据结构
     */
    private static class DriverBaseAggregate {
        Set<String> images = new HashSet<>();
        String startDate;
        String endDate;
        int summaryOrderCount;
        int driverCancelCount;
        double onlineDuration;
        double peakOnlineDuration;
        int okServerDayCount;
        double serveDuration;
        double incomeAmount;
        double rewardAmount;
        int performanceOrderCount;
        String sourceType;
    }
    
    private DriverBaseAggregate ensureAggregate(Map<String, DriverBaseAggregate> m, String key) {
        return m.computeIfAbsent(key, k -> new DriverBaseAggregate());
    }
    
    private String rangeKey(String start, String end) {
        return (start != null ? start : "") + "|" + (end != null ? end : "");
    }
    
    private String preferSource(String current, String incoming) {
        if (current != null && !current.isEmpty()) {
            return current;
        }
        return incoming != null ? incoming : "";
    }
    
    private String joinImages(Set<String> imgs) {
        if (imgs == null || imgs.isEmpty()) {
            return "";
        }
        return String.join("||", imgs);
    }
    
    private BSaasPersonalHomepage firstPersonalHomepage(List<BSaasPersonalHomepage> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    private String firstProductType(List<BSaasPassengerDetail> details) {
        if (details == null) {
            return "";
        }
        for (BSaasPassengerDetail d : details) {
            if (d.getProductType() != null && !d.getProductType().isEmpty()) {
                return d.getProductType();
            }
        }
        return "";
    }
    
    private int boolToInt(boolean b) {
        return b ? 1 : 0;
    }
}

