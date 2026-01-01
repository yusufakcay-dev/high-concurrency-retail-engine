package io.github.yusufakcay_dev.payment_service.scheduler;

import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import io.github.yusufakcay_dev.payment_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that runs every minute to check for expired pending payments
 * Payments pending for more than 5 minutes are marked as FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduler {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYMENT_RESULT_TOPIC = "payment-results";
    private static final int PAYMENT_EXPIRATION_MINUTES = 5;

    /**
     * Runs every minute (60000 ms) to check for expired payments
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingPayments() {
        log.debug("Running payment expiration check...");

        // Calculate expiration time (5 minutes ago)
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(PAYMENT_EXPIRATION_MINUTES);

        // Find all pending payments older than 5 minutes
        List<Payment> expiredPayments = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING,
                expirationTime);

        if (expiredPayments.isEmpty()) {
            log.debug("No expired payments found");
            return;
        }

        log.info("Found {} expired pending payments", expiredPayments.size());

        // Process each expired payment
        for (Payment payment : expiredPayments) {
            try {
                // Mark payment as FAILED
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment expired - not completed within 5 minutes");
                paymentRepository.save(payment);

                log.info("Payment {} expired and marked as FAILED (Order: {})",
                        payment.getId(), payment.getOrderId());

                // Publish failure event to Kafka for Order Service
                PaymentResultEvent resultEvent = PaymentResultEvent.builder()
                        .paymentId(payment.getId().toString())
                        .orderId(payment.getOrderId())
                        .status("FAILED")
                        .failureReason("Payment expired - not completed within 5 minutes")
                        .build();

                kafkaTemplate.send(PAYMENT_RESULT_TOPIC, payment.getOrderId().toString(), resultEvent);
                log.info("Payment expiration event published to Kafka for order: {}", payment.getOrderId());

            } catch (Exception e) {
                log.error("Error processing expired payment {}: {}", payment.getId(), e.getMessage(), e);
            }
        }

        log.info("Completed processing {} expired payments", expiredPayments.size());
    }
}
