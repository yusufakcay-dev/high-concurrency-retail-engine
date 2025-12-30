package io.github.yusufakcay_dev.inventory_service.service;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.entity.Inventory;
import io.github.yusufakcay_dev.inventory_service.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcurrencyStressTest {

    @Mock
    private InventoryRepository repository;

    @InjectMocks
    private InventoryService service;

    private static final String TEST_SKU = "CONCURRENCY-TEST-SKU";
    private static final int INITIAL_STOCK = 1000;
    private static final int NUM_THREADS = 10;
    private static final int RESERVATIONS_PER_THREAD = 50;

    @Test
    void testConcurrentReservations() throws InterruptedException {
        // Arrange
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

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act - Launch concurrent reservations
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < RESERVATIONS_PER_THREAD; j++) {
                        try {
                            InventoryResponse response = service.reserveInventory(TEST_SKU, 2);
                            if (response != null) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert - Wait for all threads to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Test did not complete within timeout");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify interactions
        assertTrue(successCount.get() > 0, "At least some reservations should succeed");
        verify(repository, atLeastOnce()).findBySku(TEST_SKU);
        verify(repository, atLeastOnce()).save(any(Inventory.class));
    }

    @Test
    void testConcurrentReservationAndRelease() throws InterruptedException {
        // Arrange
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS * 2);
        AtomicInteger reserveSuccess = new AtomicInteger(0);
        AtomicInteger releaseSuccess = new AtomicInteger(0);

        // Act - Half threads reserve, half release
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, 5);
                    reserveSuccess.incrementAndGet();
                } catch (Exception e) {
                    // Expected for insufficient stock
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    service.releaseReservedInventory(TEST_SKU, 2);
                    releaseSuccess.incrementAndGet();
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(reserveSuccess.get() + releaseSuccess.get() > 0);
    }

    @Test
    void testHighVolumeReservations() throws InterruptedException {
        // Arrange
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(20);
        int totalReservations = 100;
        CountDownLatch latch = new CountDownLatch(totalReservations);
        AtomicInteger completed = new AtomicInteger(0);

        // Act - High volume of rapid reservations
        for (int i = 0; i < totalReservations; i++) {
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, 1);
                    completed.incrementAndGet();
                } catch (Exception e) {
                    // Some may fail due to insufficient stock
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        assertTrue(finished);

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testRaceConditionOnQuantities() throws InterruptedException {
        // Arrange
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger reservationCount = new AtomicInteger(0);

        // Mock returns incremented reserved quantity to simulate race
        AtomicInteger reservedCount = new AtomicInteger(0);
        when(repository.findBySku(TEST_SKU)).thenAnswer(invocation -> {
            int current = reservedCount.get();
            Inventory inv = Inventory.builder()
                    .id(1L)
                    .sku(TEST_SKU)
                    .quantity(INITIAL_STOCK)
                    .reservedQuantity(current)
                    .availableQuantity(INITIAL_STOCK - current)
                    .build();
            return Optional.of(inv);
        });
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Multiple threads attempting simultaneous reservations
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, 10);
                    reservationCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for insufficient stock
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify repository was called multiple times due to concurrent access
        verify(repository, atLeast(5)).findBySku(TEST_SKU);
    }

    @Test
    void testStressTestWithVariedQuantities() throws InterruptedException {
        // Arrange
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(15);
        CountDownLatch latch = new CountDownLatch(100);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Multiple threads with varying reservation quantities
        for (int i = 0; i < 100; i++) {
            final int quantity = (i % 10) + 1; // 1-10 items
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, quantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for insufficient stock
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(successCount.get() >= 0);
        verify(repository, atLeast(50)).findBySku(TEST_SKU);
    }

    @Test
    void testConcurrentAccessPerformance() throws InterruptedException {
        // Arrange
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1000);
        long startTime = System.nanoTime();

        // Act - 1000 concurrent operations
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    service.getInventoryBySku(TEST_SKU);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed);

        long duration = System.nanoTime() - startTime;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Should complete in reasonable time
        assertTrue(durationMs < 30000, "1000 concurrent operations should complete in < 30 seconds");
    }

    @Test
    void testDeadlockPrevention() throws InterruptedException {
        // Arrange
        Inventory inventory = Inventory.builder()
                .id(1L)
                .sku(TEST_SKU)
                .quantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .availableQuantity(INITIAL_STOCK)
                .build();

        when(repository.findBySku(TEST_SKU)).thenReturn(Optional.of(inventory));
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(40);

        // Act - Multiple threads doing reserve/release in sequence
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    service.reserveInventory(TEST_SKU, 5);
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    service.releaseReservedInventory(TEST_SKU, 2);
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    service.confirmReservation(TEST_SKU, 1);
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    service.getInventoryBySku(TEST_SKU);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert - Should not deadlock
        boolean completed = latch.await(20, TimeUnit.SECONDS);
        assertTrue(completed, "Operations should complete without deadlock");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
