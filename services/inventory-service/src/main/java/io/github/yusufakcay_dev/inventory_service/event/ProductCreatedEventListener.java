package io.github.yusufakcay_dev.inventory_service.event;

import io.github.yusufakcay_dev.inventory_service.dto.ProductCreatedEvent;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCreatedEventListener {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "${app.topics.product-created:product-created-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        try {
            log.info("Received ProductCreatedEvent for SKU: {} with eventId: {}", event.getSku(), event.getEventId());

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

}
