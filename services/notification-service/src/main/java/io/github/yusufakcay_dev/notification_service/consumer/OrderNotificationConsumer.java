package io.github.yusufakcay_dev.notification_service.consumer;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import io.github.yusufakcay_dev.notification_service.service.EmailService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final EmailService emailService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000), autoCreateTopics = "true", include = {
            Exception.class }, topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "order-notifications", groupId = "notification-service-group")
    public void handleOrderNotification(OrderNotificationEvent event) {
        log.info("Received order notification event for order: {}", event.getOrderId());
        emailService.sendOrderNotification(event);
        log.info("Notification processed successfully for order: {}", event.getOrderId());
    }

    @DltHandler
    public void handleDlt(Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("Dead Letter Queue - Failed to send notification after all retries. " +
                "Event: {}, Topic: {}, Offset: {}, Error: {}",
                event, topic, offset, exceptionMessage);

        // Try to extract information if it's an OrderNotificationEvent
        if (event instanceof OrderNotificationEvent) {
            OrderNotificationEvent notificationEvent = (OrderNotificationEvent) event;
            log.error("DLT - OrderId: {}, CustomerEmail: {}",
                    notificationEvent.getOrderId(), notificationEvent.getCustomerEmail());
        }
        // Store failed notification for manual retry or alert support team
    }
}
