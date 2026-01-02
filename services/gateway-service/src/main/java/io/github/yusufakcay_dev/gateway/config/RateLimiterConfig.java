package io.github.yusufakcay_dev.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class RateLimiterConfig {

    /**
     * Redis Rate Limiter Configuration
     * - replenishRate: Number of requests per second allowed (steady state)
     * - burstCapacity: Maximum number of requests allowed in a burst
     * - requestedTokens: Number of tokens consumed per request
     */
    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter() {
        // 20 requests/second steady rate, burst up to 40
        return new RedisRateLimiter(20, 40, 1);
    }

    /**
     * Key Resolver - Determines how to identify unique clients for rate limiting
     * Priority: X-User-Name header > IP Address > "anonymous"
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // First try authenticated user
            String username = exchange.getRequest().getHeaders().getFirst("X-User-Name");
            if (username != null && !username.isEmpty()) {
                log.debug("Rate limiting by user: {}", username);
                return Mono.just("user:" + username);
            }

            // Fall back to IP address
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            log.debug("Rate limiting by IP: {}", ip);
            return Mono.just("ip:" + ip);
        };
    }
}
