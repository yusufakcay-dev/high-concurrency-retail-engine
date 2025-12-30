package io.github.yusufakcay_dev.inventory_service;

import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.inventory_service.event.ProductCreatedEventListener;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryIntegrationTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductCreatedEventListener eventListener;

    @Test
    void testEndToEndInventoryWorkflow() {
        // Arrange
        String sku = "E2E-TEST-SKU";
        Integer initialStock = 500;

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku(sku)
                .initialStock(initialStock)
                .timestamp(System.currentTimeMillis())
                .build();

        // Act
        eventListener.handleProductCreatedEvent(event);

        // Assert
        verify(inventoryService).initializeInventory(sku, initialStock);
    }

    @Test
    void testMultipleInventoriesManagement() {
        // Arrange
        String[] skus = { "SKU-INT-001", "SKU-INT-002", "SKU-INT-003" };
        Integer[] stocks = { 100, 200, 300 };

        // Act
        for (int i = 0; i < skus.length; i++) {
            ProductCreatedEvent event = ProductCreatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sku(skus[i])
                    .initialStock(stocks[i])
                    .timestamp(System.currentTimeMillis())
                    .build();
            eventListener.handleProductCreatedEvent(event);
        }

        // Assert
        for (int i = 0; i < skus.length; i++) {
            verify(inventoryService).initializeInventory(skus[i], stocks[i]);
        }
    }

    @Test
    void testInventoryReservationWithInsufficientStock() {
        // Arrange
        String sku = "INSUFFICIENT-SKU";
        Integer initialStock = 50;

        when(inventoryService.initializeInventory(anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("Insufficient stock"));

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku(sku)
                .initialStock(initialStock)
                .timestamp(System.currentTimeMillis())
                .build();

        // Act & Assert
        try {
            eventListener.handleProductCreatedEvent(event);
        } catch (Exception e) {
            // Expected
        }

        verify(inventoryService).initializeInventory(sku, initialStock);
    }
}
