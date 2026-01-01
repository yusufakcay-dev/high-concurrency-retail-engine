package io.github.yusufakcay_dev.notification_service.consumer;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import io.github.yusufakcay_dev.notification_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "order-notifications", groupId = "notification-service-group")
    public void handleOrderNotification(OrderNotificationEvent event) {
        log.info("Received order notification event for order: {}", event.getOrderId());
        try {
            emailService.sendOrderNotification(event);
            log.info("Notification processed successfully for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process notification for order: {}", event.getOrderId(), e);
        }
    }
}
