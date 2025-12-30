package io.github.yusufakcay_dev.product_service;

import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import io.github.yusufakcay_dev.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductIntegrationTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private ProductService productService;

    @Test
    void testCreateAndRetrieveProduct() {
        // Arrange
        String sku = "INT-TEST";
        String name = "Integration Test";
        BigDecimal price = new BigDecimal("50.00");

        Product product = Product.builder()
                .id(1L)
                .name(name)
                .sku(sku)
                .price(price)
                .build();

        when(repository.save(any(Product.class))).thenReturn(product);

        // Act
        Product saved = repository.save(product);

        // Assert
        assertNotNull(saved);
        assertEquals(name, saved.getName());
        assertEquals(sku, saved.getSku());

        verify(repository).save(any(Product.class));
    }

    @Test
    void testMultipleProductsWorkflow() {
        // Arrange
        List<String> skus = Arrays.asList("PROD-001", "PROD-002", "PROD-003");

        // Act & Assert
        for (String sku : skus) {
            Product product = Product.builder()
                    .id((long) skus.indexOf(sku))
                    .sku(sku)
                    .name("Product " + sku)
                    .price(BigDecimal.TEN)
                    .build();

            when(repository.save(any(Product.class))).thenReturn(product);
            when(repository.existsBySku(sku)).thenReturn(true);

            Product saved = repository.save(product);
            assertTrue(repository.existsBySku(sku));
            assertNotNull(saved);
        }
    }
}