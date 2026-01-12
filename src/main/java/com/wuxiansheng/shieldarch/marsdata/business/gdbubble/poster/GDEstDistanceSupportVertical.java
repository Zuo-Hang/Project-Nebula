package com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster;

import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 处理 | 问题（支持竖线分隔符）
 */
@Slf4j
@Component
public class GDEstDistanceSupportVertical implements Poster {
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof GDBubbleBusiness)) {
            return business;
        }
        
        GDBubbleBusiness gb = (GDBubbleBusiness) business;
        
        if (gb.getReasonResult() == null || gb.getInput() == null) {
            return gb;
        }
        
        Double estDis = gb.getReasonResult().getEstimatedDistance();
        if (estDis == null || estDis == 0.0) {
            return gb;
        }
        
        try {
            List<Double> disRanges = gb.getInput().disRanges();
            if (disRanges == null || disRanges.size() < 2) {
                return gb;
            }
            
            double left = disRanges.get(0);
            double right = disRanges.get(1);
            
            // 场景1：识别出来的预估里程和 dis_range 匹配
            if (left < estDis && estDis < right) {
                return gb;
            }
            
            // 场景2：添加前置1之后和 dis_range 匹配
            String dis1Str = "1" + String.valueOf(estDis);
            try {
                double dis1 = Double.parseDouble(dis1Str);
                if (left < dis1 && dis1 < right) {
                    gb.getReasonResult().setEstimatedDistance(dis1);
                    return gb;
                }
            } catch (NumberFormatException e) {
                // 忽略
            }
            
            // 场景3：去掉前置1之后和 dis_range 匹配
            if (estDis < 10.0) {
                return gb;
            }
            String disStr = String.valueOf(estDis);
            if (disStr.length() > 0 && disStr.charAt(0) == '1') {
                try {
                    double dis2 = Double.parseDouble(disStr.substring(1));
                    if (left < dis2 && dis2 < right) {
                        gb.getReasonResult().setEstimatedDistance(dis2);
                        return gb;
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            
        } catch (Exception e) {
            log.warn("PostHandlerEstDistance err: {}, dis_range: {}, estimate_id: {}", 
                e.getMessage(), gb.getInput().getDisRange(), gb.getInput().getEstimateId());
        }
        
        return gb;
    }
}

