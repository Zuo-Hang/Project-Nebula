package com.wuxiansheng.shieldarch.marsdata.business.xlprice.poster;

import com.wuxiansheng.shieldarch.marsdata.business.xlprice.XLPriceRuleBusiness;
import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.config.CityMap;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 填充城市信息
 */
@Slf4j
@Component
public class XLPriceFillCity implements Poster {
    
    @Autowired
    private LLMConfigHelper llmConfigHelper;
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof XLPriceRuleBusiness)) {
            return business;
        }
        
        XLPriceRuleBusiness gb = (XLPriceRuleBusiness) business;
        
        if (gb.getData() == null || gb.getData().getRuleID() == null) {
            return gb;
        }
        
        // 使用rule_id标识城市，而不是采集商填写，避免采集商填写错误
        if (llmConfigHelper == null) {
            return gb;
        }
        Map<Integer, String> ruleIdToCityNameMap = llmConfigHelper.getXLRuleIdToCityNameMap();
        String cityName = ruleIdToCityNameMap.get(gb.getData().getRuleID());
        
        if (cityName != null) {
            gb.setCityName(cityName);
            gb.setCityId(CityMap.getCityMap().get(cityName));
        }
        
        return gb;
    }
}

