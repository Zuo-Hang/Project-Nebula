package com.wuxiansheng.shieldarch.marsdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redis配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "redis")
public class RedisConfig {
    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 服务名称（支持服务发现格式，如 "disf!service-name" 或直接 IP:Port）
     */
    private String serviceName;

    /**
     * 最大空闲连接数
     */
    private Integer maxIdle = 10;

    /**
     * 最大活跃连接数
     */
    private Integer maxActive = 100;

    /**
     * 空闲超时时间（秒）
     */
    private Integer idleTimeout = 300;

    /**
     * 认证密码
     */
    private String auth;

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connTimeout = 3000;

    /**
     * 读取超时时间（毫秒）
     */
    private Integer readTimeout = 3000;

    /**
     * 写入超时时间（毫秒）
     */
    private Integer writeTimeout = 3000;

    /**
     * 连接最大生存时间（秒）
     */
    private Integer maxConnLifetime = 3600;
}
