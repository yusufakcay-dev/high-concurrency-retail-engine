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

    @SuppressWarnings("resource")
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
    void testSaveAndFindBySku() {
        String sku = "TEST-SKU-001";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        Inventory saved = repository.save(inventory);

        assertNotNull(saved.getId());

        Optional<Inventory> found = repository.findBySku(sku);
        assertTrue(found.isPresent());
        assertEquals(sku, found.get().getSku());
        assertEquals(100, found.get().getQuantity());
    }

    @Test
    void testExistsBySku() {
        String sku = "EXISTING-SKU";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(75)
                .reservedQuantity(0)
                .availableQuantity(75)
                .build();

        repository.save(inventory);

        assertTrue(repository.existsBySku(sku));
        assertFalse(repository.existsBySku("NON-EXISTENT-SKU"));
    }

    @Test
    void testUpdateInventory() {
        String sku = "UPDATE-TEST-SKU";
        Inventory inventory = Inventory.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        Inventory saved = repository.save(inventory);
        saved.setReservedQuantity(25);
        saved.setAvailableQuantity(75);
        repository.save(saved);

        Optional<Inventory> retrieved = repository.findBySku(sku);
        assertTrue(retrieved.isPresent());
        assertEquals(25, retrieved.get().getReservedQuantity());
        assertEquals(75, retrieved.get().getAvailableQuantity());
    }
}
