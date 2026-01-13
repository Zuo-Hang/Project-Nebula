package com.wuxiansheng.shieldarch.orchestrator.config;

import com.wuxiansheng.shieldarch.statestore.LLMCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * LLM缓存配置适配器
 * 将 AppConfigService 适配为 LLMCacheService.ConfigService
 */
@Configuration
public class LLMCacheConfigAdapter {
    
    @Autowired
    private AppConfigService appConfigService;
    
    /**
     * 创建配置服务适配器
     */
    @Bean
    public LLMCacheService.ConfigService llmCacheConfigService() {
        return new LLMCacheService.ConfigService() {
            @Override
            public Map<String, String> getConfig(String namespace) {
                return appConfigService.getConfig(namespace);
            }
        };
    }
}

