package io.github.yusufakcay_dev.product_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisCacheConfig implements CachingConfigurer {

        @Bean
        public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
                log.info("===== Initializing Redis CacheManager =====");

                RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(redisConnectionFactory)
                                .cacheDefaults(cacheConfig)
                                .withCacheConfiguration("products", cacheConfig)
                                .build();
        }

        @Override
        public CacheErrorHandler errorHandler() {
                return new SimpleCacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception,
                                        org.springframework.cache.Cache cache,
                                        Object key) {
                                log.error("Cache GET error for key '{}' in cache '{}': {}", key, cache.getName(),
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception,
                                        org.springframework.cache.Cache cache,
                                        Object key, Object value) {
                                log.error("Cache PUT error for key '{}' in cache '{}': {}", key, cache.getName(),
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception,
                                        org.springframework.cache.Cache cache,
                                        Object key) {
                                log.error("Cache EVICT error for key '{}' in cache '{}': {}", key, cache.getName(),
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception,
                                        org.springframework.cache.Cache cache) {
                                log.error("Cache CLEAR error in cache '{}': {}", cache.getName(),
                                                exception.getMessage());
                        }
                };
        }
}
