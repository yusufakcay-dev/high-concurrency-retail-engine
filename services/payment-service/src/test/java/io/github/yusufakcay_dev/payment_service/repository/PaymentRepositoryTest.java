package io.github.yusufakcay_dev.payment_service.repository;

import io.github.yusufakcay_dev.payment_service.config.TestContainersConfig;
import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest extends TestContainersConfig {

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private Payment createPayment(UUID orderId, String stripePaymentId, PaymentStatus status) {
        return Payment.builder()
                .orderId(orderId)
                .amount(new BigDecimal("99.99"))
                .email("test@example.com")
                .customerEmail("test@example.com")
                .currency("USD")
                .status(status)
                .stripePaymentId(stripePaymentId)
                .stripePaymentUrl("https://checkout.stripe.com/pay/" + stripePaymentId)
                .build();
    }

    @Test
    void shouldSavePaymentAndGenerateUUID() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "cs_test_123", PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByStripePaymentId() {
        String stripePaymentId = "cs_test_unique";
        Payment payment = createPayment(UUID.randomUUID(), stripePaymentId, PaymentStatus.PENDING);
        paymentRepository.save(payment);

        assertThat(paymentRepository.findByStripePaymentId(stripePaymentId)).isPresent();
        assertThat(paymentRepository.findByStripePaymentId("nonexistent")).isEmpty();
    }

    @Test
    void shouldFindByOrderId() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "cs_test_order", PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
        assertThat(paymentRepository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldFindExpiredPendingPayments() {
        Payment pendingPayment = createPayment(UUID.randomUUID(), "cs_pending", PaymentStatus.PENDING);
        Payment successPayment = createPayment(UUID.randomUUID(), "cs_success", PaymentStatus.SUCCESS);
        paymentRepository.saveAll(List.of(pendingPayment, successPayment));

        LocalDateTime futureTime = LocalDateTime.now().plusMinutes(10);
        List<Payment> expired = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, futureTime);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
