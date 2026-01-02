package io.github.yusufakcay_dev.order_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    /**
     * Global Circuit Breaker Configuration
     * Applies to all Feign clients by default
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> globalCircuitBreakerConfig() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // Wait 30 seconds before transitioning from OPEN to HALF_OPEN
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        // Minimum 5 calls before calculating failure rate
                        .minimumNumberOfCalls(5)
                        // 50% failure rate threshold to open circuit
                        .failureRateThreshold(50)
                        // Consider these exceptions as failures
                        .recordExceptions(Exception.class)
                        // Use sliding window of last 10 calls
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        // In HALF_OPEN state, allow 3 test calls
                        .permittedNumberOfCallsInHalfOpenState(3)
                        // Automatically transition from OPEN to HALF_OPEN
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        // Timeout for each call
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .build());
    }

    /**
     * Custom Circuit Breaker for Inventory Service
     * More aggressive settings since inventory is critical
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> inventoryCircuitBreakerConfig() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // Faster recovery for inventory service
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .minimumNumberOfCalls(3)
                        .failureRateThreshold(50)
                        .slidingWindowSize(5)
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build()),
                "inventory-service");
    }
}
