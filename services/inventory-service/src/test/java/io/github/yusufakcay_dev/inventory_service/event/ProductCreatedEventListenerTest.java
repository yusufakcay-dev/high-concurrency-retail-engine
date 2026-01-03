package io.github.yusufakcay_dev.inventory_service.event;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.inventory_service.service.IdempotencyService;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
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

        @Mock
        private IdempotencyService idempotencyService;

        @InjectMocks
        private ProductCreatedEventListener listener;

        @BeforeEach
        void setUp() {
                when(idempotencyService.getInventoryEventKey(anyString()))
                                .thenAnswer(invocation -> "idempotency:inventory:" + invocation.getArgument(0));
                when(idempotencyService.isFirstProcessing(anyString())).thenReturn(true);
        }

        @Test
        void testHandleProductCreatedEventSuccess() {
                String sku = "TEST-SKU";
                Integer initialStock = 100;

                ProductCreatedEvent event = ProductCreatedEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .sku(sku)
                                .initialStock(initialStock)
                                .timestamp(System.currentTimeMillis())
                                .build();

                InventoryResponse response = InventoryResponse.builder()
                                .sku(sku)
                                .quantity(initialStock)
                                .reservedQuantity(0)
                                .availableQuantity(initialStock)
                                .build();

                when(inventoryService.initializeInventory(sku, initialStock)).thenReturn(response);

                listener.handleProductCreatedEvent(event);

                verify(inventoryService).initializeInventory(sku, initialStock);
        }

        @Test
        void testHandleProductCreatedEventThrowsException() {
                ProductCreatedEvent event = ProductCreatedEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .sku("INVALID-SKU")
                                .initialStock(100)
                                .timestamp(System.currentTimeMillis())
                                .build();

                when(inventoryService.initializeInventory(anyString(), anyInt()))
                                .thenThrow(new RuntimeException("Service error"));

                assertThrows(RuntimeException.class, () -> listener.handleProductCreatedEvent(event));
                verify(inventoryService).initializeInventory("INVALID-SKU", 100);
        }
}
