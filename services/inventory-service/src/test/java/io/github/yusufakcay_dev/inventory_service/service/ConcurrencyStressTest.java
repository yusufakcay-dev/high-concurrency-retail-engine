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

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcurrencyStressTest {

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

    private static final String TEST_SKU = "CONCURRENCY-TEST-SKU";
    private static final int INITIAL_STOCK = 1000;

    @BeforeEach
    void setUp() throws InterruptedException {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void testConcurrentReservations() throws InterruptedException {
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inv = invocation.getArgument(0);
            return Inventory.builder()
                    .id(inv.getId())
                    .sku(inv.getSku())
                    .quantity(inv.getQuantity())
                    .reservedQuantity(inv.getReservedQuantity())
                    .availableQuantity(inv.getAvailableQuantity())
                    .build();
        });

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        try {
                            InventoryResponse response = service.reserveInventory(TEST_SKU, 2);
                            if (response != null) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Expected for some concurrent calls
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Test did not complete within timeout");
        executor.shutdown();

        assertTrue(successCount.get() > 0, "At least some reservations should succeed");
        verify(repository, atLeastOnce()).findBySku(TEST_SKU);
    }

    @Test
    void testConcurrentReservationAndRelease() throws InterruptedException {
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger operationCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, 5);
                    operationCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    service.releaseReservedInventory(TEST_SKU, 2);
                    operationCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed);
        executor.shutdown();

        assertTrue(operationCount.get() > 0);
    }
}
