package com.wuxiansheng.shieldarch.marsdata.business.xlprice.sinker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiansheng.shieldarch.marsdata.business.xlprice.DistancePriceSegment;
import com.wuxiansheng.shieldarch.marsdata.business.xlprice.XLPriceRuleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.xlprice.XLRuleData;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.sinker.HiveSinker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小拉计价Hive Sinker
 */
@Slf4j
@Component
public class XLPriceHiveSinker extends HiveSinker {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof XLPriceRuleBusiness)) {
            return;
        }
        
        XLPriceRuleBusiness gb = (XLPriceRuleBusiness) business;
        
        if (gb.getData() == null) {
            return;
        }
        
        XLRuleData data = gb.getData();
        
        // 为每个小时（0-23）生成一条Hive记录
        for (int i = 0; i < 24; i++) {
            try {
                List<DistancePriceSegment> longDistanceRuleDetails = 
                    data.getLongDistanceRule().periodDetail(i);
                
                String longFeeDetail = objectMapper.writeValueAsString(longDistanceRuleDetails);
                
                HiveRaw raw = new HiveRaw();
                raw.setDate(gb.getDate());
                raw.setPeriod(i);
                raw.setCityName(gb.getCityName());
                raw.setCityID(gb.getCityId());
                raw.setStartDis(data.getStartDistance());
                raw.setStartDur(data.getStartTimeLen());
                raw.setStartFee(data.getStartPriceRule().periodPrice(i));
                raw.setDisCharge(data.getExceedDistanceRule().periodPrice(i));
                raw.setDurCharge(data.getExceedTimeLenRule().periodPrice(i));
                raw.setLongFeeDetail(longFeeDetail);
                
                printToHive(raw, business.getName(), gb.getMsgTimestamp());
                
            } catch (Exception e) {
                log.warn("json.Marshal err: {}, period: {}", e.getMessage(), i, e);
            }
        }
    }
    
    /**
     * Hive行数据
     */
    @Data
    private static class HiveRaw {
        // 日期
        private String date;
        // 时间段（0-23）
        private Integer period;
        // 城市名称
        private String cityName;
        // 城市ID
        private Integer cityID;
        // 起步距离(km)
        private Double startDis;
        // 起步时长(分钟)
        private Double startDur;
        // 起步费用
        private Double startFee;
        // 距离附加费
        private Double disCharge;
        // 时长附加费
        private Double durCharge;
        // 长途费用详情（JSON字符串）
        private String longFeeDetail;
    }
}

