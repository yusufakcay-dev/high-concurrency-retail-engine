package io.github.yusufakcay_dev.inventory_service.event;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCreatedEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductCreatedEventListener listener;

    @Test
    void testHandleProductCreatedEventSuccess() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        String sku = "TEST-SKU";
        Integer initialStock = 100;

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .eventId(eventId)
                .sku(sku)
                .initialStock(initialStock)
                .timestamp(System.currentTimeMillis())
                .build();

        InventoryResponse inventoryResponse = InventoryResponse.builder()
                .sku(sku)
                .quantity(initialStock)
                .reservedQuantity(0)
                .availableQuantity(initialStock)
                .build();

        when(inventoryService.initializeInventory(sku, initialStock))
                .thenReturn(inventoryResponse);

        // Act
        listener.handleProductCreatedEvent(event);

        // Assert
        verify(inventoryService).initializeInventory(sku, initialStock);
    }

    @Test
    void testHandleProductCreatedEventWithDifferentSkus() {
        // Arrange
        String sku1 = "SKU-001";
        String sku2 = "SKU-002";
        Integer stock1 = 50;
        Integer stock2 = 75;

        ProductCreatedEvent event1 = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku(sku1)
                .initialStock(stock1)
                .timestamp(System.currentTimeMillis())
                .build();

        ProductCreatedEvent event2 = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku(sku2)
                .initialStock(stock2)
                .timestamp(System.currentTimeMillis())
                .build();

        when(inventoryService.initializeInventory(anyString(), anyInt()))
                .thenReturn(InventoryResponse.builder().build());

        // Act
        listener.handleProductCreatedEvent(event1);
        listener.handleProductCreatedEvent(event2);

        // Assert
        verify(inventoryService).initializeInventory(sku1, stock1);
        verify(inventoryService).initializeInventory(sku2, stock2);
    }

    @Test
    void testHandleProductCreatedEventThrowsException() {
        // Arrange
        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku("INVALID-SKU")
                .initialStock(100)
                .timestamp(System.currentTimeMillis())
                .build();

        when(inventoryService.initializeInventory(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> listener.handleProductCreatedEvent(event));
        verify(inventoryService).initializeInventory("INVALID-SKU", 100);
    }

    @Test
    void testHandleProductCreatedEventWithLargeStock() {
        // Arrange
        String sku = "BIG-STOCK-SKU";
        Integer largeStock = 1000000;

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sku(sku)
                .initialStock(largeStock)
                .timestamp(System.currentTimeMillis())
                .build();

        InventoryResponse response = InventoryResponse.builder()
                .sku(sku)
                .quantity(largeStock)
                .build();

        when(inventoryService.initializeInventory(sku, largeStock))
                .thenReturn(response);

        // Act
        listener.handleProductCreatedEvent(event);

        // Assert
        verify(inventoryService).initializeInventory(sku, largeStock);
    }
}
