package io.github.yusufakcay_dev.inventory_service.event;

import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.inventory_service.service.IdempotencyService;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCreatedEventListener {

    private final InventoryService inventoryService;
    private final IdempotencyService idempotencyService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000), autoCreateTopics = "true", include = {
            Exception.class }, topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "${app.topics.product-created:product-created-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        try {
            log.info("Received ProductCreatedEvent for SKU: {} with eventId: {}", event.getSku(), event.getEventId());

            // Idempotency check using Redis
            String idempotencyKey = idempotencyService.getInventoryEventKey(event.getEventId());
            if (!idempotencyService.isFirstProcessing(idempotencyKey)) {
                log.warn("Duplicate ProductCreatedEvent detected for eventId: {}. Skipping processing.",
                        event.getEventId());
                return;
            }

            inventoryService.initializeInventory(
                    event.getSku(),
                    event.getInitialStock());

            log.info("Successfully initialized inventory for SKU: {}", event.getSku());
        } catch (Exception e) {
            log.error("Failed to process ProductCreatedEvent for SKU: {} with eventId: {}",
                    event.getSku(), event.getEventId(), e);
            throw e;
        }
    }

    @DltHandler
    public void handleDlt(Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("Dead Letter Queue - Failed to process ProductCreatedEvent after all retries. " +
                "Event: {}, Topic: {}, Offset: {}, Error: {}",
                event, topic, offset, exceptionMessage);

        // Try to extract information if it's a ProductCreatedEvent
        if (event instanceof ProductCreatedEvent) {
            ProductCreatedEvent productEvent = (ProductCreatedEvent) event;
            log.error("DLT - SKU: {}, EventId: {}",
                    productEvent.getSku(), productEvent.getEventId());
        }
        // Here you could:
        // 1. Send alert to monitoring system
        // 2. Store in database for manual review
        // 3. Publish to a special topic for manual intervention
    }

}
