package com.wuxiansheng.shieldarch.marsdata.business.bsaas.sinker;

import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasDriverDetail;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasInput;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasOrderListItem;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasPassengerDetail;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.sinker.HiveSinker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单信息Hive Sinker
 */
@Slf4j
@Component
public class OrderInfoHiveSinker extends HiveSinker {
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!(business instanceof BSaasBusiness)) {
            return;
        }
        
        BSaasBusiness bs = (BSaasBusiness) business;
        if (bs.getReasonResult() == null) {
            return;
        }
        
        List<Map<String, Object>> records = buildOrderRecords(bctx, bs);
        for (Map<String, Object> record : records) {
            printToHive(record, business.getName(), bs.getMsgTimestamp());
        }
    }
    
    /**
     * 构建订单记录
     */
    private List<Map<String, Object>> buildOrderRecords(BusinessContext bctx, BSaasBusiness bs) {
        List<Map<String, Object>> records = new ArrayList<>();
        
        if (bs.getReasonResult().getOrderList() == null) {
            return records;
        }
        
        BSaasInput.Meta meta = bs.getInput() != null ? bs.getInput().getMeta() : null;
        String platform = bs.getInput() != null ? bs.getInput().getBusiness() : "";
        String[] dtHour = deriveSubmitTime(bs.getInput());
        String recordType = bs.getInput() != null && bs.getInput().getSubLine() != null ? 
            bs.getInput().getSubLine().trim() : bs.getName();
        if (recordType.isEmpty()) {
            recordType = bs.getName();
        }
        
        for (BSaasOrderListItem order : bs.getReasonResult().getOrderList()) {
            BSaasDriverDetail driver = order.getDriverDetailRef();
            BSaasPassengerDetail passenger = order.getPassengerDetailRef();
            
            // 乘客明细字段
            Double startFeeSf = passenger != null ? passenger.getStartFeeSf() : null;
            Double startDis = passenger != null ? passenger.getStartDis() : null;
            Double disChargeSf = passenger != null ? passenger.getDisChargeSf() : null;
            Double overDistance = passenger != null ? passenger.getOverDistance() : null;
            Double durChargeSf = passenger != null ? passenger.getDurChargeSf() : null;
            Double overDuration = passenger != null ? passenger.getOverDuration() : null;
            Double longFeeSf = passenger != null ? passenger.getLongFeeSf() : null;
            Double longDis = passenger != null ? passenger.getLongDis() : null;
            Double dynamicFeeSf = passenger != null ? passenger.getDynamicFeeSf() : null;
            Double dynamicFactor = passenger != null ? passenger.getDynamicFactor() : null;
            Double passengerOrderPrice = passenger != null ? passenger.getPassengerOrderPrice() : null;
            
            // 司机明细字段
            Double passengerPay = driver != null ? driver.getPassengerPay() : null;
            Double passengerPreDiscountPay = driver != null ? driver.getPassengerPreDiscountPay() : null;
            Double passengerDiscount = passenger != null ? passenger.getPassengerDiscount() : 
                (driver != null ? driver.getPassengerDiscount() : null);
            Double driverIncome = driver != null ? driver.getDriverIncome() : null;
            Double driverBaseIncome = driver != null ? driver.getDriverBaseIncome() : null;
            Double infoFee = driver != null ? driver.getInfoFee() : null;
            Object takeRate = "";
            if (driver != null && driver.getTakeRateRaw() != null && !driver.getTakeRateRaw().trim().isEmpty()) {
                takeRate = valueOrEmpty(driver.getTakeRate());
            }
            Double driverReward = driver != null ? driver.getDriverReward() : null;
            Double secFee = driver != null ? driver.getSecurityServiceFee() : null;
            Double dispBefore = driver != null ? driver.getOrderDispatchServiceFeeBeforeSubsidy() : null;
            Double dispAfter = driver != null ? driver.getOrderDispatchServiceFeeAfterSubsidy() : null;
            Double infoBefore = driver != null ? driver.getInfoFeeBeforeSubsidy() : null;
            Double infoAfter = driver != null ? driver.getInfoFeeAfterSubsidy() : null;
            Double driverRemuneration = driver != null ? driver.getDriverRemuneration() : null;
            Double passengerPayFee = driver != null ? driver.getPassengerPayFee() : null;
            String passengerOtherFee = passenger != null ? passenger.getPassengerOtherFee() : "";
            
            Map<String, Object> record = new HashMap<>();
            record.put("estimate_id", meta != null ? meta.getId() : "");
            record.put("driver_id", meta != null ? meta.getDriverName() : "");
            record.put("images", order.getImageURL());
            record.put("driver_detail_images", driverImage(driver));
            record.put("passenger_detail_images", passengerImage(passenger));
            record.put("order_id", order.getOrderID());
            record.put("order_date", order.getOrderDate());
            record.put("order_time", trimTimeMinute(order.getOrderTime()));
            record.put("start_poi", "");
            record.put("end_poi", "");
            record.put("order_price", order.getAmount());
            record.put("charge_type", order.getChargeType());
            record.put("order_type", order.getOrderType());
            record.put("order_customer", order.getOrderCustomer());
            record.put("take_fee_type", order.getTakeFeeType());
            record.put("order_channel", order.getOrderChannel());
            record.put("product_type", passengerProductType(passenger));
            record.put("start_fee_sf", valueOrEmpty(startFeeSf));
            record.put("start_dis", valueOrEmpty(startDis));
            record.put("dis_charge_sf", valueOrEmpty(disChargeSf));
            record.put("over_distance", valueOrEmpty(overDistance));
            record.put("dur_charge_sf", valueOrEmpty(durChargeSf));
            record.put("over_duration", valueOrEmpty(overDuration));
            record.put("long_fee_sf", valueOrEmpty(longFeeSf));
            record.put("long_dis", valueOrEmpty(longDis));
            record.put("dynamic_fee_sf", valueOrEmpty(dynamicFeeSf));
            record.put("dynamic_factor", valueOrEmpty(dynamicFactor));
            record.put("passenger_order_price", valueOrEmpty(passengerOrderPrice));
            record.put("passenger_pay", valueOrEmpty(passengerPay));
            record.put("passenger_pre_discount_pay", valueOrEmpty(passengerPreDiscountPay));
            record.put("passenger_discount", valueOrEmpty(passengerDiscount));
            record.put("driver_income", valueOrEmpty(driverIncome));
            record.put("driver_base_income", valueOrEmpty(driverBaseIncome));
            record.put("info_fee", valueOrEmpty(infoFee));
            record.put("take_rate", takeRate);
            record.put("driver_reward", valueOrEmpty(driverReward));
            record.put("city_name", meta != null ? meta.getCityName() : "");
            record.put("order_status", order.getStatus());
            record.put("supplier_name", meta != null ? meta.getSupplierName() : "");
            record.put("city_illegal", boolToInt(meta != null && meta.getCityIllegal() != null && meta.getCityIllegal()));
            record.put("platform", platform);
            record.put("dt", dtHour[0]);
            record.put("hour", dtHour.length > 1 ? dtHour[1] : "23");
            record.put("type", recordType);
            record.put("security_service_fee", valueOrEmpty(secFee));
            record.put("dispatch_service_fee_before", valueOrEmpty(dispBefore));
            record.put("dispatch_service_fee_after", valueOrEmpty(dispAfter));
            record.put("info_fee_before_subsidy", valueOrEmpty(infoBefore));
            record.put("info_fee_after_subsidy", valueOrEmpty(infoAfter));
            record.put("driver_remuneration", valueOrEmpty(driverRemuneration));
            record.put("passenger_pay_fee", valueOrEmpty(passengerPayFee));
            record.put("driver_other_fee", driverOtherFee(driver));
            record.put("passenger_other_fee", passengerOtherFee);
            
            records.add(record);
        }
        
        return records;
    }
    
    private String driverImage(BSaasDriverDetail driver) {
        return driver != null ? driver.getImageURL() : "";
    }
    
    private String passengerImage(BSaasPassengerDetail passenger) {
        return passenger != null ? passenger.getImageURL() : "";
    }
    
    private String passengerProductType(BSaasPassengerDetail passenger) {
        return passenger != null ? passenger.getProductType() : "";
    }
    
    private String driverOtherFee(BSaasDriverDetail driver) {
        return driver != null ? driver.getOtherFee() : "";
    }
    
    private Object valueOrEmpty(Double val) {
        return val != null ? val : "";
    }
    
    private String trimTimeMinute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] parts = value.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return value;
    }
    
    private String[] deriveSubmitTime(BSaasInput input) {
        String dt = "";
        String hour = "";
        
        if (input != null) {
            if (input.getSubmitDate() != null && !input.getSubmitDate().isEmpty()) {
                dt = input.getSubmitDate().replace("/", "-");
            } else if (input.getSubmitDateTime() != null) {
                dt = input.getSubmitDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            
            if (input.getSubmitDateTime() != null) {
                hour = input.getSubmitDateTime().format(DateTimeFormatter.ofPattern("HH"));
            }
        }
        
        return new String[]{dt, hour};
    }
    
    private int boolToInt(boolean b) {
        return b ? 1 : 0;
    }
}

