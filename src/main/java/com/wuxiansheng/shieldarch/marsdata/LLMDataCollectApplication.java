package com.wuxiansheng.shieldarch.marsdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Project Nebula - LLM Data Collect Service
 * 主启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({"com.wuxiansheng.shieldarch.marsdata.business.*.sinker", "com.wuxiansheng.shieldarch.marsdata.io"})
public class LLMDataCollectApplication {

    public static void main(String[] args) {
        SpringApplication.run(LLMDataCollectApplication.class, args);
    }
}

