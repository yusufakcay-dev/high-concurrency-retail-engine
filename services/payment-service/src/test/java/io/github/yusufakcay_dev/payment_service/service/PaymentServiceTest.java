package io.github.yusufakcay_dev.payment_service.service;

import io.github.yusufakcay_dev.payment_service.dto.StripeWebhookEvent;
import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import io.github.yusufakcay_dev.payment_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @InjectMocks
        private PaymentService paymentService;

        @Captor
        private ArgumentCaptor<Payment> paymentCaptor;

        @Captor
        private ArgumentCaptor<PaymentResultEvent> eventCaptor;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(paymentService, "stripeApiKey", "sk_test_fake_key");
                ReflectionTestUtils.setField(paymentService, "successUrl", "http://localhost:3000/success");
                ReflectionTestUtils.setField(paymentService, "cancelUrl", "http://localhost:3000/cancel");
        }

        private Payment createTestPayment(UUID orderId, String stripePaymentId, PaymentStatus status) {
                Payment payment = Payment.builder()
                                .orderId(orderId)
                                .amount(new BigDecimal("99.99"))
                                .email("test@example.com")
                                .customerEmail("test@example.com")
                                .currency("USD")
                                .status(status)
                                .stripePaymentId(stripePaymentId)
                                .stripePaymentUrl("https://checkout.stripe.com/pay/" + stripePaymentId)
                                .build();
                payment.setCreatedAt(LocalDateTime.now());
                payment.setUpdatedAt(LocalDateTime.now());
                return payment;
        }

        private StripeWebhookEvent createWebhookEvent(String type, String stripePaymentId, String paymentStatus) {
                return StripeWebhookEvent.builder()
                                .type(type)
                                .data(StripeWebhookEvent.StripeData.builder()
                                                .object(StripeWebhookEvent.StripeObject.builder()
                                                                .id(stripePaymentId)
                                                                .paymentStatus(paymentStatus)
                                                                .customerEmail("test@example.com")
                                                                .build())
                                                .build())
                                .build();
        }

        @Test
        void shouldMarkPaymentAsSuccessOnCompletedCheckout() {
                UUID orderId = UUID.randomUUID();
                UUID paymentId = UUID.randomUUID();
                String stripePaymentId = "cs_test_success";

                Payment payment = createTestPayment(orderId, stripePaymentId, PaymentStatus.PENDING);
                ReflectionTestUtils.setField(payment, "id", paymentId);

                when(paymentRepository.findByStripePaymentId(stripePaymentId)).thenReturn(Optional.of(payment));
                when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

                paymentService.handleStripeWebhook(
                                createWebhookEvent("checkout.session.completed", stripePaymentId, "paid"));

                verify(paymentRepository).save(paymentCaptor.capture());
                assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

                verify(kafkaTemplate).send(eq("payment-results"), eq(orderId.toString()), eventCaptor.capture());
                assertThat(eventCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        void shouldMarkPaymentAsFailedOnExpiredCheckout() {
                UUID orderId = UUID.randomUUID();
                String stripePaymentId = "cs_test_expired";

                Payment payment = createTestPayment(orderId, stripePaymentId, PaymentStatus.PENDING);
                ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());

                when(paymentRepository.findByStripePaymentId(stripePaymentId)).thenReturn(Optional.of(payment));
                when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

                paymentService.handleStripeWebhook(
                                createWebhookEvent("checkout.session.expired", stripePaymentId, "unpaid"));

                verify(paymentRepository).save(paymentCaptor.capture());
                assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(paymentCaptor.getValue().getFailureReason()).contains("checkout.session.expired");
        }

        @Test
        void shouldSkipAlreadyProcessedPayment() {
                String stripePaymentId = "cs_test_already_processed";
                Payment payment = createTestPayment(UUID.randomUUID(), stripePaymentId, PaymentStatus.SUCCESS);
                ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());

                when(paymentRepository.findByStripePaymentId(stripePaymentId)).thenReturn(Optional.of(payment));

                paymentService.handleStripeWebhook(
                                createWebhookEvent("checkout.session.completed", stripePaymentId, "paid"));

                verify(paymentRepository, never()).save(any(Payment.class));
                verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        void shouldThrowExceptionWhenPaymentNotFound() {
                String stripePaymentId = "cs_test_not_found";
                when(paymentRepository.findByStripePaymentId(stripePaymentId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> paymentService.handleStripeWebhook(
                                createWebhookEvent("checkout.session.completed", stripePaymentId, "paid")))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Payment not found");
        }

        @Test
        void shouldPublishKafkaEventWithFailureReason() {
                UUID orderId = UUID.randomUUID();
                String stripePaymentId = "cs_test_failure";

                Payment payment = createTestPayment(orderId, stripePaymentId, PaymentStatus.PENDING);
                ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());

                when(paymentRepository.findByStripePaymentId(stripePaymentId)).thenReturn(Optional.of(payment));
                when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

                paymentService.handleStripeWebhook(
                                createWebhookEvent("checkout.session.expired", stripePaymentId, "unpaid"));

                verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
                assertThat(eventCaptor.getValue().getStatus()).isEqualTo("FAILED");
                assertThat(eventCaptor.getValue().getFailureReason()).contains("checkout.session.expired");
        }
}
