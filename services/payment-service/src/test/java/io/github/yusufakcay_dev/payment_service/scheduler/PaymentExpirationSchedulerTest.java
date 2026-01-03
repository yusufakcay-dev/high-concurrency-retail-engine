package io.github.yusufakcay_dev.payment_service.scheduler;

import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import io.github.yusufakcay_dev.payment_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationSchedulerTest {

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @InjectMocks
        private PaymentExpirationScheduler scheduler;

        @Captor
        private ArgumentCaptor<Payment> paymentCaptor;

        @Captor
        private ArgumentCaptor<PaymentResultEvent> eventCaptor;

        private Payment createTestPayment(UUID orderId) {
                Payment payment = Payment.builder()
                                .orderId(orderId)
                                .amount(new BigDecimal("99.99"))
                                .email("test@example.com")
                                .customerEmail("test@example.com")
                                .currency("USD")
                                .status(PaymentStatus.PENDING)
                                .stripePaymentId("cs_test_" + UUID.randomUUID())
                                .stripePaymentUrl("https://checkout.stripe.com/pay/cs_test")
                                .build();
                ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now().minusMinutes(10));
                return payment;
        }

        @Test
        void shouldMarkExpiredPendingPaymentAsFailed() {
                UUID orderId = UUID.randomUUID();
                Payment expiredPayment = createTestPayment(orderId);

                when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(List.of(expiredPayment));
                when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

                scheduler.expirePendingPayments();

                verify(paymentRepository).save(paymentCaptor.capture());
                assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(paymentCaptor.getValue().getFailureReason()).contains("Payment expired");
        }

        @Test
        void shouldPublishKafkaEventForExpiredPayment() {
                UUID orderId = UUID.randomUUID();
                Payment expiredPayment = createTestPayment(orderId);

                when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(List.of(expiredPayment));
                when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

                scheduler.expirePendingPayments();

                verify(kafkaTemplate).send(eq("payment-results"), eq(orderId.toString()), eventCaptor.capture());
                assertThat(eventCaptor.getValue().getStatus()).isEqualTo("FAILED");
                assertThat(eventCaptor.getValue().getFailureReason()).contains("Payment expired");
        }

        @Test
        void shouldDoNothingWhenNoExpiredPayments() {
                when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());

                scheduler.expirePendingPayments();

                verify(paymentRepository, never()).save(any(Payment.class));
                verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
}
