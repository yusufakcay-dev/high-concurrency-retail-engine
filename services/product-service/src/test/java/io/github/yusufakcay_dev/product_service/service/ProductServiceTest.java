package io.github.yusufakcay_dev.product_service.service;

import io.github.yusufakcay_dev.product_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.product_service.dto.ProductRequest;
import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    @InjectMocks
    private ProductService service;

    @Captor
    private ArgumentCaptor<ProductCreatedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "productCreatedTopic", "product-created-topic");
    }

    @Test
    void testCreateProductSuccess() {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setSku("SKU123");
        request.setPrice(new BigDecimal("99.99"));
        request.setInitialStock(10);

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .sku("SKU123")
                .price(new BigDecimal("99.99"))
                .active(true)
                .build();

        when(repository.existsBySku("SKU123")).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        @SuppressWarnings("unchecked")
        SendResult<String, ProductCreatedEvent> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(ProductCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        ProductResponse response = service.createProduct(request);

        // Assert
        assertNotNull(response);
        assertEquals("Test Product", response.getName());
        assertEquals("SKU123", response.getSku());
        assertEquals(new BigDecimal("99.99"), response.getPrice());
        assertTrue(response.getActive());

        verify(repository).existsBySku("SKU123");
        verify(repository).save(any(Product.class));
        verify(kafkaTemplate).send(eq("product-created-topic"), eq("SKU123"), eventCaptor.capture());

        ProductCreatedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent.getEventId());
        assertEquals("SKU123", capturedEvent.getSku());
        assertEquals(10, capturedEvent.getInitialStock());
        assertNotNull(capturedEvent.getTimestamp());
    }

    @Test
    void testCreateProductDuplicateSku() {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setSku("DUPLICATE");

        when(repository.existsBySku("DUPLICATE")).thenReturn(true);

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> service.createProduct(request));
        verify(repository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void testGetAllProducts() {
        // Arrange
        Product product1 = Product.builder().id(1L).name("P1").sku("S1").price(new BigDecimal("10")).active(true)
                .build();
        Product product2 = Product.builder().id(2L).name("P2").sku("S2").price(new BigDecimal("20")).active(true)
                .build();
        Page<Product> page = new PageImpl<>(List.of(product1, product2));

        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(page);

        // Act
        Page<ProductResponse> result = service.getAllProducts(pageable);

        // Assert
        assertEquals(2, result.getTotalElements());
        verify(repository).findAll(pageable);
    }

    @Test
    void testGetProductByIdSuccess() {
        // Arrange
        Product product = Product.builder().id(1L).name("Test").sku("SKU").price(new BigDecimal("50")).active(true)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        // Act
        ProductResponse response = service.getProductById(1L);

        // Assert
        assertNotNull(response);
        assertEquals("Test", response.getName());
        verify(repository).findById(1L);
    }

    @Test
    void testGetProductByIdNotFound() {
        // Arrange
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> service.getProductById(999L));
    }

    @Test
    void testCreateProductKafkaSendSuccess() {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setName("Kafka Test");
        request.setSku("SKU123");
        request.setPrice(new BigDecimal("99.99"));
        request.setInitialStock(10);

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Kafka Test")
                .sku("SKU123")
                .price(new BigDecimal("99.99"))
                .active(true)
                .build();

        when(repository.existsBySku("SKU123")).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        @SuppressWarnings("unchecked")
        SendResult<String, ProductCreatedEvent> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(ProductCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        ProductResponse response = service.createProduct(request);

        // Assert
        assertNotNull(response);
        verify(kafkaTemplate).send(eq("product-created-topic"), eq("SKU123"), eventCaptor.capture());

        ProductCreatedEvent event = eventCaptor.getValue();
        assertNotNull(event.getEventId());
        assertEquals("SKU123", event.getSku());
        assertEquals(10, event.getInitialStock());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void testCreateProductKafkaFailureDoesNotRollback() {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setName("Kafka Fail Test");
        request.setSku("SKU999");
        request.setPrice(new BigDecimal("50.00"));
        request.setInitialStock(5);

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Kafka Fail Test")
                .sku("SKU999")
                .price(new BigDecimal("50.00"))
                .active(true)
                .build();

        when(repository.existsBySku("SKU999")).thenReturn(false);
        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        CompletableFuture<SendResult<String, ProductCreatedEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka send failed"));
        when(kafkaTemplate.send(anyString(), anyString(), any(ProductCreatedEvent.class)))
                .thenReturn(failedFuture);

        // Act
        ProductResponse response = service.createProduct(request);

        // Assert - Product should still be created even if Kafka fails
        assertNotNull(response);
        assertEquals("Kafka Fail Test", response.getName());
        verify(repository).save(any(Product.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any(ProductCreatedEvent.class));
    }
}