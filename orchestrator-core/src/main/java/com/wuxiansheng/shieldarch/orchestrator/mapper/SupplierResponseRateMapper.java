package com.wuxiansheng.shieldarch.orchestrator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuxiansheng.shieldarch.orchestrator.entity.SupplierResponseRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 供应商应答概率 Mapper
 */
@Mapper
public interface SupplierResponseRateMapper extends BaseMapper<SupplierResponseRate> {
    
    /**
     * 根据城市名称和供应商名称查询应答概率
     * 
     * @param cityName 城市名称
     * @param partnerName 供应商名称
     * @return 应答概率，如果不存在返回 null
     */
    @Select("SELECT response_rate FROM supplier_response_rate " +
            "WHERE city_name = #{cityName} AND partner_name = #{partnerName} AND deleted = 0 " +
            "LIMIT 1")
    BigDecimal getResponseRate(@Param("cityName") String cityName, @Param("partnerName") String partnerName);
}

