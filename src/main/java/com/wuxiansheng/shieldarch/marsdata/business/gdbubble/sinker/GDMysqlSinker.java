package com.wuxiansheng.shieldarch.marsdata.business.gdbubble.sinker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleInput;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.GDBubbleReasonResult;
import com.wuxiansheng.shieldarch.marsdata.business.gdbubble.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.config.GlobalConfig;
import com.wuxiansheng.shieldarch.marsdata.io.MysqlWrapper;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Sinker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 高德冒泡MySQL Sinker
 * 将高德冒泡数据批量写入MySQL
 */
@Slf4j
@Component
public class GDMysqlSinker implements Sinker {
    
    @Autowired(required = false)
    private MysqlWrapper mysqlWrapper;
    
    @Autowired(required = false)
    private GlobalConfig globalConfig;
    
    @Value("${mysql.switch:true}")
    private boolean mysqlSwitch;
    
    @Autowired(required = false)
    private GDBubbleMysqlMapper mysqlMapper;
    
    @Override
    public void sink(BusinessContext bctx, Business business) {
        if (!mysqlSwitch) {
            log.info("mysql switch is false, stop writing mysql");
            return;
        }
        
        if (!(business instanceof GDBubbleBusiness)) {
            return;
        }
        
        GDBubbleBusiness gb = (GDBubbleBusiness) business;
        
        if (gb.getReasonResult() == null || gb.getReasonResult().getSuppliersInfo() == null) {
            return;
        }
        
        try {
            List<GDBubbleMysqlRow> rows = newMysqlRows(gb);
            if (rows.isEmpty()) {
                return;
            }
            
            batchUpsert(rows);
        } catch (Exception e) {
            log.warn("batchUpsert err: {}, business: {}", e.getMessage(), business.getName(), e);
        }
    }
    
    /**
     * 批量Upsert
     * 使用MyBatis Plus的saveOrUpdateBatch实现
     */
    private void batchUpsert(List<GDBubbleMysqlRow> rows) {
        if (mysqlMapper == null) {
            log.warn("mysqlMapper is null, cannot write to mysql");
            return;
        }
        
        // 使用MyBatis Plus的saveOrUpdateBatch
        // 注意：需要根据estimate_bubble_id来判断是否存在，如果存在则更新，否则插入
        for (GDBubbleMysqlRow row : rows) {
            // 先查询是否存在
            LambdaQueryWrapper<GDBubbleMysqlRow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(GDBubbleMysqlRow::getEstimateBubbleId, row.getEstimateBubbleId());
            
            GDBubbleMysqlRow existing = mysqlMapper.selectOne(queryWrapper);
            if (existing != null) {
                // 更新
                row.setId(existing.getId());
                mysqlMapper.updateById(row);
            } else {
                // 插入
                mysqlMapper.insert(row);
            }
        }
    }
    
    /**
     * 创建MySQL行数据列表
     */
    private List<GDBubbleMysqlRow> newMysqlRows(GDBubbleBusiness gb) {
        List<GDBubbleMysqlRow> res = new ArrayList<>();
        
        GDBubbleInput input = gb.getInput();
        GDBubbleReasonResult reasonResult = gb.getReasonResult();
        
        if (input == null || reasonResult == null || reasonResult.getSuppliersInfo() == null) {
            return res;
        }
        
        for (int i = 0; i < reasonResult.getSuppliersInfo().size(); i++) {
            ReasonSupplierResult supplierInfo = reasonResult.getSuppliersInfo().get(i);
            
            GDBubbleMysqlRow row = new GDBubbleMysqlRow();
            row.setEstimateBubbleId(String.format("%s_0_%d", input.getEstimateId(), i));
            row.setEstimateId(input.getEstimateId());
            row.setCityId(input.getCityId() != null ? input.getCityId() : 0);
            row.setCityName(input.getCityName() != null ? input.getCityName() : "");
            row.setCreateTime(reasonResult.getCreationTime() != null ? reasonResult.getCreationTime() : "");
            
            // 拼接图片URL
            StringJoiner joiner = new StringJoiner(" || ");
            if (input.getBubbleImageUrls() != null) {
                for (String url : input.getBubbleImageUrls()) {
                    joiner.add(url);
                }
            }
            row.setBubbleImageUrl(joiner.toString());
            
            row.setBubbleAggregation("");
            row.setEstArriveTime("");
            row.setEstDistance(reasonResult.getEstimatedDistance() != null ? reasonResult.getEstimatedDistance() : 0.0);
            row.setEstDuration(reasonResult.getEstimatedTime() != null ? reasonResult.getEstimatedTime().intValue() : 0);
            row.setEstPay(supplierInfo.getEstPrice() != null ? supplierInfo.getEstPrice() : 0.0);
            row.setReducePrice(supplierInfo.getDiscountAmount() != null ? supplierInfo.getDiscountAmount() : 0.0);
            row.setDynamicPrice(0.0);
            row.setPartnerName(supplierInfo.getSupplier() != null ? supplierInfo.getSupplier() : "");
            row.setCarType(supplierInfo.getCarType() != null ? supplierInfo.getCarType() : "");
            row.setPhone("");
            row.setSubmitTime(input.getSubmitTime() != null ? input.getSubmitTime() : "");
            row.setCapPrice(supplierInfo.getCapPrice() != null ? String.format("%f", supplierInfo.getCapPrice()) : "0.0");
            row.setStartDistrict("");
            row.setStartPoi(reasonResult.getStartPoint() != null ? reasonResult.getStartPoint() : "");
            row.setEndPoi(reasonResult.getEndPoint() != null ? reasonResult.getEndPoint() : "");
            row.setPartyOffer("");
            row.setOtherPrice(supplierInfo.getOtherPrice() != null ? String.format("%f", supplierInfo.getOtherPrice()) : "0.0");
            row.setTime("");
            row.setRidePlatformId(input.getRidePlatformId() != null ? String.valueOf(input.getRidePlatformId()) : "");
            row.setRidePlatformName(input.getRidePlatformName() != null ? input.getRidePlatformName() : "");
            row.setExpenseTime("");
            row.setUserType("");
            row.setResponseDurationWj("");
            row.setEstArriveTimeWj("");
            row.setEstArriveDistanceWj("");
            row.setTCoinReducePrice("");
            row.setIsFromOcrResult("false");
            row.setSubmitName(input.getSubmitName() != null ? input.getSubmitName() : "");
            row.setTimeRange(input.getTimeRange() != null ? input.getTimeRange() : "");
            row.setDisRange(input.getDisRange() != null ? input.getDisRange() : "");
            row.setIsStation("false"); // 写入mysql的，均为非场站。场站的不要写入，避免污染线上环境
            row.setStationName(input.getStationName() != null ? input.getStationName() : "");
            row.setSystemMsg("");
            row.setReduceType(supplierInfo.getDiscountType() != null ? supplierInfo.getDiscountType() : "");
            row.setExtraPrice("");
            row.setDataCheckStatus(gb.getCityCheck() != null ? gb.getCityCheck() : "");
            row.setStartLng(gb.getStartLNG() != null ? gb.getStartLNG() : "");
            row.setStartLat(gb.getStartLAT() != null ? gb.getStartLAT() : "");
            row.setRouteType("");
            row.setRoute("");
            row.setDistrictType(input.getDistrictType() != null ? input.getDistrictType() : "");
            row.setDistrictName(input.getDistrictName() != null ? input.getDistrictName() : "");
            
            res.add(row);
        }
        
        return res;
    }
    
}

