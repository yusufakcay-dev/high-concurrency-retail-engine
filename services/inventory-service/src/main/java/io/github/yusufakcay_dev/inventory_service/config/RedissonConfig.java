package io.github.yusufakcay_dev.inventory_service.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration for distributed locks
 * Prevents race conditions in high-concurrency inventory operations
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(String.format("redis://%s:%d", redisHost, redisPort))
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(10)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setTimeout(3000)
                .setConnectTimeout(5000);

        return Redisson.create(config);
    }
}
