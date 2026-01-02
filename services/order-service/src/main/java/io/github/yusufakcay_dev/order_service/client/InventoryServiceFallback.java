package io.github.yusufakcay_dev.order_service.client;

import io.github.yusufakcay_dev.order_service.dto.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for InventoryServiceClient
 * Called when the circuit breaker is OPEN or when Inventory Service is
 * unavailable
 */
@Slf4j
@Component
public class InventoryServiceFallback implements InventoryServiceClient {

    @Override
    public InventoryResponse reserve(String sku, Integer quantity) {
        log.error("CIRCUIT BREAKER OPEN: Failed to reserve {} units for SKU: {}. Inventory service unavailable.",
                quantity, sku);
        throw new InventoryServiceUnavailableException(
                "Inventory service is currently unavailable. Please try again later.");
    }

    @Override
    public InventoryResponse release(String sku, Integer quantity) {
        log.error("CIRCUIT BREAKER OPEN: Failed to release {} units for SKU: {}. Inventory service unavailable.",
                quantity, sku);
        // For release, we log but don't throw - will need manual reconciliation
        log.warn("MANUAL INTERVENTION REQUIRED: Release {} units for SKU: {} failed due to circuit breaker",
                quantity, sku);
        return null;
    }

    @Override
    public InventoryResponse confirm(String sku, Integer quantity) {
        log.error("CIRCUIT BREAKER OPEN: Failed to confirm {} units for SKU: {}. Inventory service unavailable.",
                quantity, sku);
        // For confirm, we log but don't throw - will need manual reconciliation
        log.warn("MANUAL INTERVENTION REQUIRED: Confirm {} units for SKU: {} failed due to circuit breaker",
                quantity, sku);
        return null;
    }

    /**
     * Custom exception for inventory service unavailability
     */
    public static class InventoryServiceUnavailableException extends RuntimeException {
        public InventoryServiceUnavailableException(String message) {
            super(message);
        }
    }
}
