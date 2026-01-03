package io.github.yusufakcay_dev.notification_service.consumer;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import io.github.yusufakcay_dev.notification_service.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
class OrderNotificationConsumerTest {

    @SuppressWarnings("resource")
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withExposedPorts(9093);

    @DynamicPropertySource
    static void setKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, OrderNotificationEvent> kafkaTemplate;

    @SuppressWarnings("removal")
    @MockBean
    private EmailService emailService;

    @Test
    void testHandleOrderNotification_PaidStatus_CallsEmailService() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("PAID")
                .amount(new BigDecimal("100.00"))
                .message("Payment successful")
                .build();

        // When
        kafkaTemplate.send("order-notifications", event.getOrderId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(emailService, times(1))
                        .sendOrderNotification(any(OrderNotificationEvent.class)));
    }

    @Test
    void testHandleOrderNotification_FailedStatus_CallsEmailService() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("FAILED")
                .amount(new BigDecimal("100.00"))
                .message("Payment declined")
                .build();

        // When
        kafkaTemplate.send("order-notifications", event.getOrderId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(emailService, atLeastOnce())
                        .sendOrderNotification(any(OrderNotificationEvent.class)));
    }

    @Test
    void testHandleOrderNotification_EmailServiceThrowsException_RetriesAndEventuallyGoesDLT() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("invalid@test.com")
                .status("PAID")
                .amount(new BigDecimal("100.00"))
                .build();

        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendOrderNotification(any(OrderNotificationEvent.class));

        // When
        kafkaTemplate.send("order-notifications", event.getOrderId().toString(), event);

        // Then - Should retry multiple times (configured to 4 attempts in consumer)
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(emailService, atLeast(2))
                        .sendOrderNotification(any(OrderNotificationEvent.class)));
    }

    @Test
    void testHandleOrderNotification_MultipleEvents_AllProcessed() {
        // Given
        OrderNotificationEvent event1 = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer1@test.com")
                .status("PAID")
                .amount(new BigDecimal("50.00"))
                .build();

        OrderNotificationEvent event2 = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer2@test.com")
                .status("FAILED")
                .amount(new BigDecimal("75.00"))
                .build();

        // When
        kafkaTemplate.send("order-notifications", event1.getOrderId().toString(), event1);
        kafkaTemplate.send("order-notifications", event2.getOrderId().toString(), event2);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(emailService, atLeast(2))
                        .sendOrderNotification(any(OrderNotificationEvent.class)));
    }
}
