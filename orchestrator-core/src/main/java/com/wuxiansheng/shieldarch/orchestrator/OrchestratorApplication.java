package com.wuxiansheng.shieldarch.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI Agent Orchestrator 主启动类
 *
 * @author Generated
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.wuxiansheng.shieldarch.orchestrator",
    "com.wuxiansheng.shieldarch.stepexecutors",
    "com.wuxiansheng.shieldarch.governance",
    "com.wuxiansheng.shieldarch.statestore"
})
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
