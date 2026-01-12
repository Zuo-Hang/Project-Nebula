package com.wuxiansheng.shieldarch.marsdata.business.couponsp;

import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.sinker.HiveSinker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.StringJoiner;

/**
 * 券包人群标签识别Hive Sinker
 */
@Slf4j
@Component
public class CouponSPHiveSinker extends HiveSinker {
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof CouponSpecialPopulationBusiness)) {
            return;
        }
        
        CouponSpecialPopulationBusiness gb = (CouponSpecialPopulationBusiness) business;
        
        for (int i = 0; i < gb.getReasonResults().size(); i++) {
            CouponReasonResult reasonResult = gb.getReasonResults().get(i);
            HiveRow row = newHiveRow(gb.getInput(), reasonResult, i + 1);
            printToHive(row, business.getName(), gb.getMsgTimestamp());
        }
        
        // 兜底，如果没有券，依然打印一条，此时index = 0
        if (gb.getReasonResults().isEmpty()) {
            HiveRow row = newHiveRow(gb.getInput(), new CouponReasonResult(), 0);
            printToHive(row, business.getName(), gb.getMsgTimestamp());
        }
    }
    
    /**
     * 创建Hive行数据
     */
    private HiveRow newHiveRow(CouponSpecialPopulationInput input, CouponReasonResult reasonResult, int idx) {
        HiveRow row = new HiveRow();
        row.setCouponSpecialPopulationInput(input);
        row.setCouponReasonResult(reasonResult);
        row.setCouponIndex(idx);
        
        // 将列表转换为逗号分隔的字符串
        StringJoiner sj1 = new StringJoiner(",");
        if (input.getActUrlsInMainPage() != null) {
            input.getActUrlsInMainPage().forEach(sj1::add);
        }
        row.setActUrlsInMainPage(sj1.toString());
        
        StringJoiner sj2 = new StringJoiner(",");
        if (input.getActUrlsInVenue() != null) {
            input.getActUrlsInVenue().forEach(sj2::add);
        }
        row.setActUrlsInVenue(sj2.toString());
        
        StringJoiner sj3 = new StringJoiner(",");
        if (input.getCouponListAndDetailUrls() != null) {
            input.getCouponListAndDetailUrls().forEach(sj3::add);
        }
        row.setCouponListAndDetailUrls(sj3.toString());
        
        return row;
    }
    
    /**
     * Hive行数据
     */
    @Data
    private static class HiveRow {
        private CouponSpecialPopulationInput couponSpecialPopulationInput;
        private CouponReasonResult couponReasonResult;
        private Integer couponIndex;
        private String actUrlsInMainPage;
        private String actUrlsInVenue;
        private String couponListAndDetailUrls;
    }
}

