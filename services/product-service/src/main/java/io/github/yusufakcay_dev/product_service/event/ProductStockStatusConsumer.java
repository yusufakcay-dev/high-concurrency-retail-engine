package io.github.yusufakcay_dev.product_service.event;

import io.github.yusufakcay_dev.product_service.dto.ProductStockStatusEvent;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductStockStatusConsumer {

    private final ProductRepository productRepository;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000), autoCreateTopics = "true", include = {
            Exception.class }, topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
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

    @DltHandler
    public void handleDlt(Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("Dead Letter Queue - Failed to process ProductStockStatusEvent after all retries. " +
                "Event: {}, Topic: {}, Offset: {}, Error: {}",
                event, topic, offset, exceptionMessage);

        // Try to extract information if it's a ProductStockStatusEvent
        if (event instanceof ProductStockStatusEvent) {
            ProductStockStatusEvent statusEvent = (ProductStockStatusEvent) event;
            log.error("DLT - SKU: {}, InStock: {}",
                    statusEvent.getSku(), statusEvent.getInStock());
        }
        // Store in database for manual review or alert operations
    }
}
