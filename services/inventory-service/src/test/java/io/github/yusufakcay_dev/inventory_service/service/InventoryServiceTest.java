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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private InventoryService service;

    @BeforeEach
    void setUp() throws InterruptedException {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void testInitializeInventorySuccess() {
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

        InventoryResponse response = service.initializeInventory(sku, initialStock);

        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(initialStock, response.getQuantity());
        assertEquals(0, response.getReservedQuantity());
        verify(repository).save(any(Inventory.class));
    }

    @Test
    void testInitializeInventoryValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory(null, 100));
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("   ", 100));
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("SKU123", -10));
        assertThrows(IllegalArgumentException.class, () -> service.initializeInventory("SKU123", null));
        verify(repository, never()).save(any());
    }

    @Test
    void testGetInventoryBySkuSuccess() {
        String sku = "TEST-SKU-002";
        Inventory inventory = Inventory.builder()
                .id(2L)
                .sku(sku)
                .quantity(200)
                .reservedQuantity(50)
                .availableQuantity(150)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));

        InventoryResponse response = service.getInventoryBySku(sku);

        assertNotNull(response);
        assertEquals(sku, response.getSku());
        assertEquals(200, response.getQuantity());
        assertEquals(50, response.getReservedQuantity());
        assertEquals(150, response.getAvailableQuantity());
    }

    @Test
    void testGetInventoryBySkuNotFound() {
        when(repository.findBySku("NON-EXISTENT")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getInventoryBySku("NON-EXISTENT"));
    }

    @Test
    void testReserveInventorySuccess() {
        String sku = "TEST-SKU-003";
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

        InventoryResponse response = service.reserveInventory(sku, 30);

        assertEquals(100, response.getQuantity());
        assertEquals(40, response.getReservedQuantity());
        assertEquals(60, response.getAvailableQuantity());
    }

    @Test
    void testReserveInventoryInsufficientStock() throws InterruptedException {
        String sku = "TEST-SKU-004";
        Inventory inventory = Inventory.builder()
                .id(4L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));

        assertThrows(ResponseStatusException.class, () -> service.reserveInventory(sku, 150));
        verify(repository, never()).save(any(Inventory.class));
    }

    @Test
    void testConfirmReservationSuccess() {
        String sku = "TEST-SKU-005";
        Inventory inventory = Inventory.builder()
                .id(5L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(30)
                .availableQuantity(70)
                .build();

        Inventory updated = Inventory.builder()
                .id(5L)
                .sku(sku)
                .quantity(80)
                .reservedQuantity(10)
                .availableQuantity(70)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenReturn(updated);

        InventoryResponse response = service.confirmReservation(sku, 20);

        assertEquals(80, response.getQuantity());
        assertEquals(10, response.getReservedQuantity());
    }

    @Test
    void testReleaseReservedInventorySuccess() {
        String sku = "TEST-SKU-006";
        Inventory inventory = Inventory.builder()
                .id(6L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(50)
                .availableQuantity(50)
                .build();

        Inventory updated = Inventory.builder()
                .id(6L)
                .sku(sku)
                .quantity(100)
                .reservedQuantity(35)
                .availableQuantity(65)
                .build();

        when(repository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenReturn(updated);

        InventoryResponse response = service.releaseReservedInventory(sku, 15);

        assertEquals(35, response.getReservedQuantity());
        assertEquals(65, response.getAvailableQuantity());
    }
}
