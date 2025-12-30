package io.github.yusufakcay_dev.inventory_service.service;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.entity.Inventory;
import io.github.yusufakcay_dev.inventory_service.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository repository;

    @InjectMocks
    private InventoryService service;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testInitializeInventorySuccess() {
        // Arrange
        String sku = "TEST-SKU-001";
        Integer initialStock = 100;

        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(sku)
                .quantity(initialStock)
                .reservedQuantity(0)
                .availableQuantity(initialStock)
                .build();

        when(repository.existsBySku(sku)).thenReturn(false);
        when(repository.save(any(Inventory.class))).thenReturn(inventory);

        // Act
        InventoryResponse response = service.initializeInventory(sku, initialStock);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(initialStock, response.getQuantity());
        assertEquals(0, response.getReservedQuantity());
        assertEquals(initialStock, response.getAvailableQuantity());

        verify(repository).existsBySku(sku);
        verify(repository).save(any(Inventory.class));
    }

    @Test
    void testInitializeInventoryAlreadyExists() {
        // Arrange
        String sku = "EXISTING-SKU";
        Integer initialStock = 50;

        Inventory existingInventory = Inventory.builder()
                .id(1L)
                .sku(sku)
                .quantity(initialStock)
                .reservedQuantity(0)
                .availableQuantity(initialStock)
                .build();

        when(repository.existsBySku(sku)).thenReturn(true);
        when(repository.findBySku(sku)).thenReturn(Optional.of(existingInventory));

        // Act
        InventoryResponse response = service.initializeInventory(sku, initialStock);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(initialStock, response.getQuantity());

        verify(repository).existsBySku(sku);
        verify(repository, never()).save(any(Inventory.class));
    }

    @Test
    void testInitializeInventoryNullSku() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory(null, 100));
        verify(repository, never()).save(any());
    }

    @Test
    void testInitializeInventoryBlankSku() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("   ", 100));
        verify(repository, never()).save(any());
    }

    @Test
    void testInitializeInventoryNegativeStock() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("SKU123", -10));
        verify(repository, never()).save(any());
    }

    @Test
    void testInitializeInventoryNullStock() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("SKU123", null));
        verify(repository, never()).save(any());
    }

    @Test
    void testGetInventoryBySkuSuccess() {
        // Arrange
        String sku = "TEST-SKU-002";
        Inventory inventory = Inventory.builder()
                .id(2L)
                .sku(sku)
                .quantity(200)
                .reservedQuantity(50)
                .availableQuantity(150)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));

        // Act
        InventoryResponse response = service.getInventoryBySku(sku);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(200, response.getQuantity());
        assertEquals(50, response.getReservedQuantity());
        assertEquals(150, response.getAvailableQuantity());

        verify(repository).findBySku(sku);
    }

    @Test
    void testGetInventoryBySkuNotFound() {
        // Arrange
        String sku = "NON-EXISTENT";
        when(repository.findBySku(sku)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> service.getInventoryBySku(sku));
        verify(repository).findBySku(sku);
    }

    @Test
    void testGetInventoryBySkuNullSku() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.getInventoryBySku(null));
        verify(repository, never()).findBySku(any());
    }

    @Test
    void testReserveInventorySuccess() {
        // Arrange
        String sku = "TEST-SKU-003";
        int quantityToReserve = 30;

        Inventory inventory = Inventory.builder()
                .id(3L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .build();

        Inventory updatedInventory = Inventory.builder()
                .id(3L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(40)
                .availableQuantity(60)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenReturn(updatedInventory);

        // Act
        InventoryResponse response = service.reserveInventory(sku, quantityToReserve);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(100, response.getQuantity());
        assertEquals(40, response.getReservedQuantity());
        assertEquals(60, response.getAvailableQuantity());

        verify(repository).findBySku(sku);
        verify(repository).save(any(Inventory.class));
    }

    @Test
    void testReserveInventoryInsufficientStock() {
        // Arrange
        String sku = "TEST-SKU-004";
        int quantityToReserve = 150;

        Inventory inventory = Inventory.builder()
                .id(4L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> service.reserveInventory(sku, quantityToReserve));
        verify(repository).findBySku(sku);
        verify(repository, never()).save(any(Inventory.class));
    }

    @Test
    void testConfirmReservationSuccess() {
        // Arrange
        String sku = "TEST-SKU-005";
        int quantityToConfirm = 20;

        Inventory inventory = Inventory.builder()
                .id(5L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(30)
                .availableQuantity(70)
                .build();

        Inventory updatedInventory = Inventory.builder()
                .id(5L)
                .sku(sku)
                .quantity(80)
                .reservedQuantity(10)
                .availableQuantity(70)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenReturn(updatedInventory);

        // Act
        InventoryResponse response = service.confirmReservation(sku, quantityToConfirm);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(80, response.getQuantity());
        assertEquals(10, response.getReservedQuantity());

        verify(repository).findBySku(sku);
        verify(repository).save(any(Inventory.class));
    }

    @Test
    void testReleaseReservedInventorySuccess() {
        // Arrange
        String sku = "TEST-SKU-006";
        int quantityToRelease = 15;

        Inventory inventory = Inventory.builder()
                .id(6L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(50)
                .availableQuantity(50)
                .build();

        Inventory updatedInventory = Inventory.builder()
                .id(6L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(35)
                .availableQuantity(65)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenReturn(updatedInventory);

        // Act
        InventoryResponse response = service.releaseReservedInventory(sku, quantityToRelease);

        // Assert
        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(35, response.getReservedQuantity());
        assertEquals(65, response.getAvailableQuantity());

        verify(repository).findBySku(sku);
        verify(repository).save(any(Inventory.class));
    }
}
