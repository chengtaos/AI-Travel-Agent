package com.ct.ai.agent.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RateLimiterConfiguration {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        // 流式接口限流配置：每秒最多10个请求，允许突发5个
        RateLimiterConfig streamApiConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        RateLimiterConfig toolCallConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(100)) // 可选：建议统一设置
                .build();

        return RateLimiterRegistry.of(Map.of(
                "streamApiLimiter", streamApiConfig,
                "toolCallLimiter", toolCallConfig
        ));
    }

    @Bean
    public RateLimiter streamApiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("streamApiLimiter");
    }

    @Bean
    public RateLimiter toolCallRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("toolCallLimiter");
    }
}