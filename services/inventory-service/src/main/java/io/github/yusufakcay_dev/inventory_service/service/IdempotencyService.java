package io.github.yusufakcay_dev.inventory_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Check if an event has already been processed using Redis setIfAbsent.
     * Returns true if this is the first time processing (key was set successfully).
     * Returns false if the event was already processed (key already exists).
     */
    public boolean isFirstProcessing(String key) {
        return isFirstProcessing(key, DEFAULT_TTL);
    }

    /**
     * Check if an event has already been processed with custom TTL.
     */
    public boolean isFirstProcessing(String key, Duration ttl) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "processed", ttl);
            boolean isFirst = Boolean.TRUE.equals(result);

            if (isFirst) {
                log.debug("First processing for key: {}", key);
            } else {
                log.warn("Duplicate event detected for key: {}", key);
            }

            return isFirst;
        } catch (Exception e) {
            log.error("Redis idempotency check failed for key: {}. Proceeding with caution.", key, e);
            // Fail open: if Redis is down, allow processing but log error
            return true;
        }
    }

    /**
     * Generate idempotency key for inventory events.
     */
    public String getInventoryEventKey(String eventId) {
        return "idempotency:inventory:" + eventId;
    }

    /**
     * Generate idempotency key for order paid events.
     */
    public String getOrderPaidKey(String orderId) {
        return "idempotency:order:paid:" + orderId;
    }

    /**
     * Manually remove an idempotency key (useful for testing or compensation).
     */
    public void removeKey(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Removed idempotency key: {}", key);
        } catch (Exception e) {
            log.error("Failed to remove idempotency key: {}", key, e);
        }
    }
}
