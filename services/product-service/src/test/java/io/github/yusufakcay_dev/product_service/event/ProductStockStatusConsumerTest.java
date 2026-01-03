package io.github.yusufakcay_dev.product_service.event;

import io.github.yusufakcay_dev.product_service.dto.ProductStockStatusEvent;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductStockStatusConsumerTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductStockStatusConsumer productStockStatusConsumer;

    private Product testProduct;
    private ProductStockStatusEvent testEvent;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(100.0))
                .sku("TEST-SKU-001")
                .active(true)
                .inStock(true)
                .build();

        testEvent = ProductStockStatusEvent.builder()
                .sku("TEST-SKU-001")
                .inStock(true)
                .build();
    }

    @Test
    void testHandleStockStatusEvent_Success_UpdatesProductInStock() {
        // Arrange
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productStockStatusConsumer.handleStockStatusEvent(testEvent);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getInStock()).isEqualTo(testEvent.getInStock());
        verify(productRepository, times(1)).findBySku("TEST-SKU-001");
        verify(productRepository, times(1)).save(testProduct);
    }

    @Test
    void testHandleStockStatusEvent_ProductNotFound_ThrowsException() {
        // Arrange
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productStockStatusConsumer.handleStockStatusEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found");

        verify(productRepository, times(1)).findBySku("TEST-SKU-001");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testHandleStockStatusEvent_UpdatesInStockToFalse() {
        // Arrange
        testProduct.setInStock(true);
        testEvent = ProductStockStatusEvent.builder()
                .sku("TEST-SKU-001")
                .inStock(false)
                .build();
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productStockStatusConsumer.handleStockStatusEvent(testEvent);

        // Assert
        assertThat(result.getInStock()).isFalse();
        verify(productRepository, times(1)).save(testProduct);
    }

    @Test
    void testHandleStockStatusEvent_UpdatesInStockToTrue() {
        // Arrange
        testProduct.setInStock(false);
        testEvent = ProductStockStatusEvent.builder()
                .sku("TEST-SKU-001")
                .inStock(true)
                .build();
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productStockStatusConsumer.handleStockStatusEvent(testEvent);

        // Assert
        assertThat(result.getInStock()).isTrue();
        verify(productRepository, times(1)).save(testProduct);
    }

    @Test
    void testHandleStockStatusEvent_RepositorySaveFailure_ThrowsException() {
        // Arrange
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThatThrownBy(() -> productStockStatusConsumer.handleStockStatusEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        verify(productRepository, times(1)).findBySku("TEST-SKU-001");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testHandleStockStatusEvent_MultipleEventsForSameProduct() {
        // Arrange
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act - first event sets to false
        ProductStockStatusEvent event1 = ProductStockStatusEvent.builder()
                .sku("TEST-SKU-001")
                .inStock(false)
                .build();
        productStockStatusConsumer.handleStockStatusEvent(event1);

        // Act - second event sets to true
        ProductStockStatusEvent event2 = ProductStockStatusEvent.builder()
                .sku("TEST-SKU-001")
                .inStock(true)
                .build();
        Product result = productStockStatusConsumer.handleStockStatusEvent(event2);

        // Assert
        assertThat(result.getInStock()).isTrue();
        verify(productRepository, times(2)).findBySku("TEST-SKU-001");
        verify(productRepository, times(2)).save(testProduct);
    }

    @Test
    void testHandleStockStatusEvent_PreservesOtherProductFields() {
        // Arrange
        when(productRepository.findBySku("TEST-SKU-001")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productStockStatusConsumer.handleStockStatusEvent(testEvent);

        // Assert - verify other fields remain unchanged
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Product");
        assertThat(result.getSku()).isEqualTo("TEST-SKU-001");
        assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(result.getActive()).isTrue();
    }

    @Test
    void testHandleStockStatusEvent_DifferentSkus() {
        // Arrange
        Product product1 = Product.builder()
                .id(1L)
                .sku("SKU-001")
                .name("Product 1")
                .price(BigDecimal.valueOf(50.0))
                .active(true)
                .inStock(true)
                .build();

        Product product2 = Product.builder()
                .id(2L)
                .sku("SKU-002")
                .name("Product 2")
                .price(BigDecimal.valueOf(75.0))
                .active(true)
                .inStock(true)
                .build();

        when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(product1));
        when(productRepository.findBySku("SKU-002")).thenReturn(Optional.of(product2));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ProductStockStatusEvent event1 = ProductStockStatusEvent.builder()
                .sku("SKU-001")
                .inStock(false)
                .build();
        Product result1 = productStockStatusConsumer.handleStockStatusEvent(event1);

        ProductStockStatusEvent event2 = ProductStockStatusEvent.builder()
                .sku("SKU-002")
                .inStock(true)
                .build();
        Product result2 = productStockStatusConsumer.handleStockStatusEvent(event2);

        // Assert
        assertThat(result1.getSku()).isEqualTo("SKU-001");
        assertThat(result1.getInStock()).isFalse();
        assertThat(result2.getSku()).isEqualTo("SKU-002");
        assertThat(result2.getInStock()).isTrue();
    }
}
