package io.github.yusufakcay_dev.product_service.service;

import io.github.yusufakcay_dev.product_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.product_service.dto.ProductRequest;
import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository repository;
    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    @Value("${app.topics.product-created:product-created-topic}")
    private String productCreatedTopic;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ProductRequest cannot be null");
        }

        if (repository.existsBySku(request.getSku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product with SKU already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                .price(request.getPrice())
                .active(true)
                .build();

        repository.save(product);

        ProductCreatedEvent event = new ProductCreatedEvent(
                UUID.randomUUID().toString(),
                request.getSku(),
                request.getInitialStock(),
                Instant.now().toEpochMilli());

        kafkaTemplate.send(productCreatedTopic, request.getSku(), event) // SKU as key
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send product event for SKU: {} with eventId: {}",
                                request.getSku(), event.getEventId(), ex);
                    } else {
                        log.info("Product event sent successfully for SKU: {} with eventId: {}",
                                request.getSku(), event.getEventId());
                    }
                });

        return mapToResponse(product);
    }

    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public ProductResponse getProductById(Long id) {
        log.info("===== CACHE MISS - Fetching product from database - ID: {} =====", id);
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        log.info("===== Product fetched from DB: {} =====", product.getId());
        return mapToResponse(product);
    }

    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return repository.findAll(pageable).map(this::mapToResponse);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .active(product.getActive())
                .inStock(product.getInStock())
                .build();
    }
}