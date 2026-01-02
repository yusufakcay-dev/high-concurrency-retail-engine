package io.github.yusufakcay_dev.order_service.kafka;

import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.service.IdempotencyService;
import io.github.yusufakcay_dev.order_service.service.OrderService;
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
public class PaymentResultConsumer {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000), autoCreateTopics = "true", include = {
            Exception.class }, topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "payment-results", groupId = "order-service-group")
    public void handlePaymentResult(PaymentResultEvent event) {
        log.info("Received payment result event: {}", event);
        // Idempotency check using Redis
        String idempotencyKey = idempotencyService.getOrderPaidKey(event.getOrderId().toString());
        if (!idempotencyService.isFirstProcessing(idempotencyKey)) {
            log.warn("Duplicate payment result event detected for orderId: {}. Skipping processing.",
                    event.getOrderId());
            return;
        }

        orderService.handlePaymentResult(event);
    }

    @DltHandler
    public void handleDlt(Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("Dead Letter Queue - Failed to process PaymentResultEvent after all retries. " +
                "Event: {}, Topic: {}, Offset: {}, Error: {}",
                event, topic, offset, exceptionMessage);

        // Try to extract information if it's a PaymentResultEvent
        if (event instanceof PaymentResultEvent) {
            PaymentResultEvent paymentEvent = (PaymentResultEvent) event;
            log.error("DLT - OrderId: {}, PaymentId: {}, Status: {}",
                    paymentEvent.getOrderId(), paymentEvent.getPaymentId(), paymentEvent.getStatus());
        }
        // Alert operations team or store for manual review
    }
}
