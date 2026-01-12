package com.wuxiansheng.shieldarch.marsdata.io;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL封装类
 * 注意：Java版本使用MyBatis Plus，MySQL指标上报需要通过MyBatis拦截器实现
 */
@Slf4j
@Component
public class MysqlWrapper {
    
    private static final String CTX_KEY_BUSINESS = "business_name";
    
    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * 初始化MySQL连接
     */
    public void initMysql() {
        if (dataSource == null) {
            log.error("MySQL数据源未配置");
            return;
        }
        
        try (Connection conn = dataSource.getConnection()) {
            log.info("MySQL连接初始化成功");
        } catch (SQLException e) {
            log.error("MySQL连接初始化失败", e);
        }
    }

    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * 业务名称标记的Context Key
     */
    public static String getBusinessContextKey() {
        return CTX_KEY_BUSINESS;
    }
}
