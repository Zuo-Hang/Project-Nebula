package com.wuxiansheng.shieldarch.marsdata.config;

import com.wuxiansheng.shieldarch.marsdata.llm.BusinessRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 业务注册配置类
 * 
 * 在应用启动时注册业务工厂、Posters、Sinkers
 */
@Slf4j
@Configuration
public class BusinessRegistrationConfig {
    
    @Autowired
    private BusinessRegistry businessRegistry;
    
    @Autowired
    private BusinessConfigService businessConfigService;
    
    /**
     * 注册所有业务依赖
     */
    @PostConstruct
    public void registerDependencies() {
        log.info("开始注册业务依赖...");
        
        // 注册具体的业务模块
        registerCouponSP();
        registerGDBubble();
        registerGDSpecialPrice();
        registerXLBubble();
        registerXLPrice();
        registerBSaas();
        
        // 在注册配置完成后调用，获取business配置
        List<String> allBusinessNames = businessRegistry.getAllBusinessNames();
        if (!allBusinessNames.isEmpty()) {
            businessConfigService.registerAllBusiness(allBusinessNames);
            log.info("已注册所有业务名称: {}", allBusinessNames);
        } else {
            log.warn("未注册任何业务，跳过业务配置注册");
        }
        
        log.info("业务依赖注册完成");
    }
    
    /**
     * 注册券包人群标签识别业务
     */
    private void registerCouponSP() {
        String businessName = "coupon_sp";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.couponsp.CouponSpecialPopulationBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.couponsp.CouponSPFilter()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.llm.sinker.LatencySinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.couponsp.CouponSPHiveSinker()
        ));
    }
    
    /**
     * 注册高德冒泡业务
     */
    private void registerGDBubble() {
        String businessName = "gd_bubble";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDEstDistanceSupportVertical(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDFilterSupplier(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDMergeSupplier(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDODCityCheck(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDFillStartPOICoord(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDFillCarType(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.poster.GDAdapterPrice()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker.GDSinkerMonitor(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker.GDHiveSinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker.GDMysqlSinker()
        ));
    }
    
    /**
     * 注册高德特价车盒子业务
     */
    private void registerGDSpecialPrice() {
        String businessName = "gd_special_price";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.GDSpecialPriceBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.poster.GDSpecialPriceFilterSupplier(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.poster.GDSpecialPriceDeduplicateSupplier()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.llm.sinker.LatencySinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.sinker.GDSpecialPriceSinkerMonitor(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.sinker.GDSpecialPriceHiveSinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.sinker.GDSpecialPriceMysqlSinker()
        ));
    }
    
    /**
     * 注册小拉冒泡业务
     */
    private void registerXLBubble() {
        String businessName = "xl_bubble";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.XLBubbleBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.poster.XLFilterSupplier(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.poster.XLMergeSupplier(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.poster.XLODCityCheck()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.llm.sinker.LatencySinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.sinker.XLSinkerMonitor(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlbubble.sinker.XLHiveSinker()
        ));
    }
    
    /**
     * 注册小拉计价业务
     */
    private void registerXLPrice() {
        String businessName = "price_rule_xl";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.xlprice.XLPriceRuleBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.xlprice.poster.XLPriceFillCity(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlprice.poster.XLPriceRefactorUnit()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.llm.sinker.LatencySinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.xlprice.sinker.XLPriceHiveSinker()
        ));
    }
    
    /**
     * 注册B SaaS业务
     */
    private void registerBSaas() {
        String businessName = "b_saas";
        businessRegistry.registerBusinessFactory(businessName, 
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusinessFactory());
        businessRegistry.registerPosters(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.OrderListNormalize(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.RecognitionResultCorrection(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.DetailNormalize(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.PageRelationMatch(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.AmountMatchValidate(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.DataCompletenessValidate(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.AnomalyFilter(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.PerformanceSummaryMerge(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster.SupplierNameNormalize()
        ));
        businessRegistry.registerSinkers(businessName, List.of(
            new com.wuxiansheng.shieldarch.marsdata.llm.sinker.LatencySinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker.MonitorSinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker.OrderInfoHiveSinker(),
            new com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker.DriverBaseHiveSinker()
        ));
    }
}

