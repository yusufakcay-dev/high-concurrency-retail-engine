package io.github.yusufakcay_dev.payment_service.integration;

import io.github.yusufakcay_dev.payment_service.config.TestContainersConfig;
import io.github.yusufakcay_dev.payment_service.dto.StripeWebhookEvent;
import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import io.github.yusufakcay_dev.payment_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.payment_service.repository.PaymentRepository;
import io.github.yusufakcay_dev.payment_service.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentIntegrationTest extends TestContainersConfig {

        @Autowired
        private PaymentRepository paymentRepository;

        @Autowired
        private PaymentService paymentService;

        private KafkaMessageListenerContainer<String, PaymentResultEvent> container;
        private BlockingQueue<ConsumerRecord<String, PaymentResultEvent>> records;

        @BeforeEach
        void setUp() throws InterruptedException {
                paymentRepository.deleteAll();
                setupKafkaConsumer();
        }

        private void setupKafkaConsumer() throws InterruptedException {
                Map<String, Object> consumerProps = new HashMap<>();
                consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
                consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
                consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
                consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
                consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentResultEvent.class.getName());
                consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

                DefaultKafkaConsumerFactory<String, PaymentResultEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                                consumerProps);
                ContainerProperties containerProperties = new ContainerProperties("payment-results");
                records = new LinkedBlockingQueue<>();

                CountDownLatch assignmentLatch = new CountDownLatch(1);
                container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
                container.setupMessageListener((MessageListener<String, PaymentResultEvent>) records::add);
                container.getContainerProperties().setConsumerRebalanceListener(
                                new org.apache.kafka.clients.consumer.ConsumerRebalanceListener() {
                                        @Override
                                        public void onPartitionsRevoked(
                                                        java.util.Collection<org.apache.kafka.common.TopicPartition> p) {
                                        }

                                        @Override
                                        public void onPartitionsAssigned(
                                                        java.util.Collection<org.apache.kafka.common.TopicPartition> p) {
                                                assignmentLatch.countDown();
                                        }
                                });
                container.start();
                assertThat(assignmentLatch.await(30, TimeUnit.SECONDS)).isTrue();
        }

        @AfterEach
        void tearDown() {
                if (container != null && container.isRunning()) {
                        container.stop();
                        await().atMost(10, TimeUnit.SECONDS).until(() -> !container.isRunning());
                }
                paymentRepository.deleteAll();
        }

        private Payment createPendingPayment(UUID orderId, String stripeSessionId) {
                return Payment.builder()
                                .orderId(orderId)
                                .amount(new BigDecimal("99.99"))
                                .email("test@example.com")
                                .customerEmail("test@example.com")
                                .currency("USD")
                                .status(PaymentStatus.PENDING)
                                .stripePaymentId(stripeSessionId)
                                .stripePaymentUrl("https://checkout.stripe.com/pay/" + stripeSessionId)
                                .build();
        }

        @Test
        void shouldPersistPaymentInDatabase() {
                UUID orderId = UUID.randomUUID();
                Payment payment = createPendingPayment(orderId, "cs_test_" + UUID.randomUUID());

                Payment saved = paymentRepository.save(payment);

                assertThat(saved.getId()).isNotNull();
                assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
        }

        @Test
        void shouldMarkPaymentAsSuccessAndPublishKafkaEvent() throws InterruptedException {
                UUID orderId = UUID.randomUUID();
                String stripeSessionId = "cs_test_" + UUID.randomUUID();
                Payment payment = paymentRepository.save(createPendingPayment(orderId, stripeSessionId));

                StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                                .type("checkout.session.completed")
                                .data(StripeWebhookEvent.StripeData.builder()
                                                .object(StripeWebhookEvent.StripeObject.builder()
                                                                .id(stripeSessionId)
                                                                .paymentStatus("paid")
                                                                .customerEmail("test@example.com")
                                                                .build())
                                                .build())
                                .build();

                paymentService.handleStripeWebhook(webhookEvent);

                await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
                        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                });

                ConsumerRecord<String, PaymentResultEvent> record = records.poll(10, TimeUnit.SECONDS);
                assertThat(record).isNotNull();
                assertThat(record.value().getStatus()).isEqualTo("SUCCESS");
                assertThat(record.value().getOrderId()).isEqualTo(orderId);
        }

        @Test
        void shouldMarkPaymentAsFailedOnExpiredWebhook() throws InterruptedException {
                UUID orderId = UUID.randomUUID();
                String stripeSessionId = "cs_test_" + UUID.randomUUID();
                Payment payment = paymentRepository.save(createPendingPayment(orderId, stripeSessionId));

                StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                                .type("checkout.session.expired")
                                .data(StripeWebhookEvent.StripeData.builder()
                                                .object(StripeWebhookEvent.StripeObject.builder()
                                                                .id(stripeSessionId)
                                                                .paymentStatus("unpaid")
                                                                .build())
                                                .build())
                                .build();

                paymentService.handleStripeWebhook(webhookEvent);

                await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
                        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
                });

                ConsumerRecord<String, PaymentResultEvent> record = records.poll(10, TimeUnit.SECONDS);
                assertThat(record).isNotNull();
                assertThat(record.value().getStatus()).isEqualTo("FAILED");
        }

        @Test
        void shouldSkipAlreadyProcessedPayment() throws InterruptedException {
                UUID orderId = UUID.randomUUID();
                String stripeSessionId = "cs_test_" + UUID.randomUUID();
                Payment payment = createPendingPayment(orderId, stripeSessionId);
                payment.setStatus(PaymentStatus.SUCCESS);
                paymentRepository.save(payment);

                StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                                .type("checkout.session.completed")
                                .data(StripeWebhookEvent.StripeData.builder()
                                                .object(StripeWebhookEvent.StripeObject.builder()
                                                                .id(stripeSessionId)
                                                                .paymentStatus("paid")
                                                                .build())
                                                .build())
                                .build();

                paymentService.handleStripeWebhook(webhookEvent);

                ConsumerRecord<String, PaymentResultEvent> record = records.poll(2, TimeUnit.SECONDS);
                assertThat(record).isNull();
        }
}
