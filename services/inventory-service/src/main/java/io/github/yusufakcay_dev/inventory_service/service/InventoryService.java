package io.github.yusufakcay_dev.inventory_service.service;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.entity.Inventory;
import io.github.yusufakcay_dev.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository repository;

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

    @Transactional
    public InventoryResponse reserveInventory(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for SKU: " + sku));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory for SKU: " + sku);
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);

        Inventory updated = repository.save(inventory);
        log.info("Reserved {} units for SKU: {}", quantity, sku);

        return mapToResponse(updated);
    }

    @Transactional
    public InventoryResponse releaseReservedInventory(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for SKU: " + sku));

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot release more than reserved quantity");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);

        Inventory updated = repository.save(inventory);
        log.info("Released {} units for SKU: {}", quantity, sku);

        return mapToResponse(updated);
    }

    @Transactional
    public InventoryResponse confirmReservation(String sku, Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for SKU: " + sku));

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot confirm more than reserved quantity");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setQuantity(inventory.getQuantity() - quantity);

        Inventory updated = repository.save(inventory);
        log.info("Confirmed reservation of {} units for SKU: {}", quantity, sku);

        return mapToResponse(updated);
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

}
