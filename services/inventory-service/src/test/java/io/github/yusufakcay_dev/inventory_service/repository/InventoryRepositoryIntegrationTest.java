package io.github.yusufakcay_dev.inventory_service.repository;

import io.github.yusufakcay_dev.inventory_service.entity.Inventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("inventory_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private InventoryRepository repository;

    @Test
    void testSaveAndRetrieveInventory() {
        // Arrange
        String sku = "TEST-SKU-001";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        // Act
        Inventory saved = repository.save(inventory);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(sku, saved.getSku());
        assertEquals(100, saved.getQuantity());
    }

    @Test
    void testFindBySkuSuccess() {
        // Arrange
        String sku = "FIND-TEST-SKU";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(50)
                .reservedQuantity(10)
                .availableQuantity(40)
                .build();

        repository.save(inventory);

        // Act
        Optional<Inventory> found = repository.findBySku(sku);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(sku, found.get().getSku());
        assertEquals(50, found.get().getQuantity());
        assertEquals(10, found.get().getReservedQuantity());
    }

    @Test
    void testExistsBySkuTrue() {
        // Arrange
        String sku = "EXISTING-SKU";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(75)
                .reservedQuantity(0)
                .availableQuantity(75)
                .build();

        repository.save(inventory);

        // Act & Assert
        assertTrue(repository.existsBySku(sku));
    }

    @Test
    void testExistsBySkuFalse() {
        // Act & Assert
        assertFalse(repository.existsBySku("NON-EXISTENT-SKU"));
    }

    @Test
    void testUpdateInventory() {
        // Arrange
        String sku = "UPDATE-TEST-SKU";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        Inventory saved = repository.save(inventory);

        // Act
        saved.setReservedQuantity(25);
        saved.setAvailableQuantity(75);
        Inventory updated = repository.save(saved);

        // Assert
        Optional<Inventory> retrieved = repository.findBySku(sku);
        assertTrue(retrieved.isPresent());
        assertEquals(25, retrieved.get().getReservedQuantity());
        assertEquals(75, retrieved.get().getAvailableQuantity());
    }

    @Test
    void testUniqueSKUConstraint() {
        // Arrange
        String sku = "UNIQUE-SKU-TEST";
        Inventory inv1 = Inventory.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        repository.save(inv1);

        Inventory inv2 = Inventory.builder()
                .sku(sku) // Same SKU
                .quantity(50)
                .reservedQuantity(0)
                .availableQuantity(50)
                .build();

        // Act & Assert - should throw due to unique constraint
        assertThrows(Exception.class, () -> repository.save(inv2));
    }

    @Test
    void testMultipleInventoriesPersistence() {
        // Arrange
        String[] skus = { "SKU-001", "SKU-002", "SKU-003" };

        // Act
        for (int i = 0; i < skus.length; i++) {
            Inventory inventory = Inventory.builder()
                    .sku(skus[i])
                    .quantity(100 + i * 10)
                    .reservedQuantity(i * 5)
                    .availableQuantity(100 + i * 10 - i * 5)
                    .build();
            repository.save(inventory);
        }

        // Assert
        assertEquals(skus.length, repository.count());
        for (String sku : skus) {
            assertTrue(repository.existsBySku(sku));
        }
    }
}
