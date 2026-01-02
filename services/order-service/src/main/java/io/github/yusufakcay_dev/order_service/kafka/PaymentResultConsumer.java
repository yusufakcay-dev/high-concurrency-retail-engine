package io.github.yusufakcay_dev.order_service.kafka;

import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.service.IdempotencyService;
import io.github.yusufakcay_dev.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "payment-results", groupId = "order-service-group")
    public void handlePaymentResult(PaymentResultEvent event) {
        log.info("Received payment result event: {}", event);
        try {
            // Idempotency check using Redis
            String idempotencyKey = idempotencyService.getOrderPaidKey(event.getOrderId().toString());
            if (!idempotencyService.isFirstProcessing(idempotencyKey)) {
                log.warn("Duplicate payment result event detected for orderId: {}. Skipping processing.",
                        event.getOrderId());
                return;
            }

            orderService.handlePaymentResult(event);
        } catch (Exception e) {
            log.error("Error processing payment result event", e);
        }
    }
}
