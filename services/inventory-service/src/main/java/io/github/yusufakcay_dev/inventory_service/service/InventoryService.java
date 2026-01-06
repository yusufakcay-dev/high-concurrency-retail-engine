package io.github.yusufakcay_dev.inventory_service.service;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.dto.ProductStockStatusEvent;
import io.github.yusufakcay_dev.inventory_service.entity.Inventory;
import io.github.yusufakcay_dev.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedissonClient redissonClient;

    private static final String PRODUCT_STOCK_STATUS_TOPIC = "product-stock-status-topic";
    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final long LOCK_WAIT_TIME = 3; // seconds
    private static final long LOCK_LEASE_TIME = 10; // seconds

    @Transactional
    public InventoryResponse initializeInventory(String sku, Integer initialStock) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (initialStock == null || initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be null or negative");
        }

        if (repository.existsBySku(sku)) {
            log.warn("Inventory already exists for SKU: {}. Skipping initialization.", sku);
            return mapToResponse(repository.findBySku(sku)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventory not found")));
        }

        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(initialStock)
                .reservedQuantity(0)
                .availableQuantity(initialStock)
                .build();

        Inventory savedInventory = repository.save(inventory);
        log.info("Inventory initialized for SKU: {} with quantity: {}", sku, initialStock);

        return mapToResponse(savedInventory);
    }

    public InventoryResponse getInventoryBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for SKU: " + sku));

        return mapToResponse(inventory);
    }

    public InventoryResponse reserveInventory(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);

        try {
            // Try to acquire lock with timeout (fail fast)
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("Failed to acquire lock for SKU: {} - concurrent operation in progress", sku);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another operation is in progress for this product. Please try again.");
            }

            log.debug("Acquired distributed lock for SKU: {}", sku);

            Inventory inventory = repository.findBySku(sku)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Inventory not found for SKU: " + sku));

            if (inventory.getAvailableQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory for SKU: " + sku);
            }

            boolean wasAvailable = inventory.getAvailableQuantity() > 0;

            inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);

            Inventory updated = repository.save(inventory);
            log.info("Reserved {} units for SKU: {}", quantity, sku);

            // If inventory hits 0, send out-of-stock event
            if (wasAvailable && updated.getAvailableQuantity() == 0) {
                publishStockStatusEvent(sku, false);
            }

            return mapToResponse(updated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for SKU: {}", sku, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process reservation due to interruption");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for SKU: {}", sku);
            }
        }
    }

    @Transactional
    public InventoryResponse releaseReservedInventory(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("Failed to acquire lock for SKU: {} - concurrent operation in progress", sku);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another operation is in progress for this product. Please try again.");
            }

            log.debug("Acquired distributed lock for SKU: {}", sku);

            Inventory inventory = repository.findBySku(sku)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Inventory not found for SKU: " + sku));

            if (inventory.getReservedQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot release more than reserved quantity");
            }

            boolean wasOutOfStock = inventory.getAvailableQuantity() == 0;

            inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);

            Inventory updated = repository.save(inventory);
            log.info("Released {} units for SKU: {}", quantity, sku);

            // If inventory becomes available again (was 0), send back-in-stock event
            if (wasOutOfStock && updated.getAvailableQuantity() > 0) {
                publishStockStatusEvent(sku, true);
            }

            return mapToResponse(updated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for SKU: {}", sku, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process release due to interruption");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for SKU: {}", sku);
            }
        }
    }

    @Transactional
    public InventoryResponse confirmReservation(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("Failed to acquire lock for SKU: {} - concurrent operation in progress", sku);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another operation is in progress for this product. Please try again.");
            }

            log.debug("Acquired distributed lock for SKU: {}", sku);

            Inventory inventory = repository.findBySku(sku)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Inventory not found for SKU: " + sku));

            if (inventory.getReservedQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot confirm more than reserved quantity");
            }

            boolean wasInStock = inventory.getQuantity() > 0;

            inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
            inventory.setQuantity(inventory.getQuantity() - quantity);

            Inventory updated = repository.save(inventory);
            log.info("Confirmed reservation of {} units for SKU: {}", quantity, sku);

            // If total quantity hits 0, send out-of-stock event
            if (wasInStock && updated.getQuantity() == 0) {
                publishStockStatusEvent(sku, false);
            }

            return mapToResponse(updated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for SKU: {}", sku, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process confirmation due to interruption");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for SKU: {}", sku);
            }
        }
    }

    @Transactional
    public InventoryResponse updateInventoryQuantity(String sku, Integer newQuantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (newQuantity == null || newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be null or negative");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("Failed to acquire lock for SKU: {} - concurrent operation in progress", sku);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another operation is in progress for this product. Please try again.");
            }

            log.debug("Acquired distributed lock for SKU: {}", sku);

            Inventory inventory = repository.findBySku(sku)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Inventory not found for SKU: " + sku));

            boolean wasAvailable = inventory.getAvailableQuantity() > 0;

            int quantityDiff = newQuantity - inventory.getQuantity();
            inventory.setQuantity(newQuantity);
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantityDiff);

            Inventory updated = repository.save(inventory);
            log.info("Updated inventory for SKU: {} to quantity: {}", sku, newQuantity);

            // Send out-of-stock event if available quantity drops to 0
            if (wasAvailable && updated.getAvailableQuantity() <= 0) {
                publishStockStatusEvent(sku, false);
            }
            // Send back-in-stock event if available quantity goes above 0
            else if (!wasAvailable && updated.getAvailableQuantity() > 0) {
                publishStockStatusEvent(sku, true);
            }

            return mapToResponse(updated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for SKU: {}", sku, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process update due to interruption");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for SKU: {}", sku);
            }
        }
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .sku(inventory.getSku())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /**
     * Publishes stock status event to Kafka for product-service to consume
     * 
     * @param sku     Product SKU
     * @param inStock true if product is back in stock, false if out of stock
     */
    private void publishStockStatusEvent(String sku, boolean inStock) {
        try {
            ProductStockStatusEvent event = ProductStockStatusEvent.builder()
                    .sku(sku)
                    .inStock(inStock)
                    .build();

            kafkaTemplate.send(PRODUCT_STOCK_STATUS_TOPIC, sku, event);
            log.info("Published stock status event for SKU: {} - inStock: {}", sku, inStock);
        } catch (Exception e) {
            log.error("Failed to publish stock status event for SKU: {}", sku, e);
        }
    }

}
