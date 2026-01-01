package io.github.yusufakcay_dev.product_service.event;

import io.github.yusufakcay_dev.product_service.dto.ProductStockStatusEvent;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductStockStatusConsumer {

    private final ProductRepository productRepository;

    @KafkaListener(topics = "product-stock-status-topic", groupId = "product-service-group")
    @Transactional
    public void handleStockStatusEvent(ProductStockStatusEvent event) {
        log.info("Received stock status event for SKU: {} - inStock: {}", event.getSku(), event.getInStock());

        try {
            Product product = productRepository.findBySku(event.getSku())
                    .orElseThrow(() -> new RuntimeException("Product not found for SKU: " + event.getSku()));

            // Update inStock status
            product.setInStock(event.getInStock());
            productRepository.save(product);

            log.info("Updated product {} inStock status to: {}", event.getSku(), event.getInStock());

        } catch (Exception e) {
            log.error("Error processing stock status event for SKU: {}", event.getSku(), e);
            throw e; // Re-throw to trigger Kafka retry
        }
    }
}
