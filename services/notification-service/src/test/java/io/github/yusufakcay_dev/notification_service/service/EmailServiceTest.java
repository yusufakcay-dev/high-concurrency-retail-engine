package io.github.yusufakcay_dev.notification_service.service;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    private static final String FROM_EMAIL = "noreply@retailengine.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void testSendOrderNotification_PaidStatus_Success() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("PAID")
                .amount(new BigDecimal("99.99"))
                .message("Payment successful")
                .build();

        // When
        emailService.sendOrderNotification(event);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendOrderNotification_FailedStatus_Success() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("FAILED")
                .amount(new BigDecimal("99.99"))
                .message("Insufficient funds")
                .build();

        // When
        emailService.sendOrderNotification(event);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendOrderNotification_FailedWithNullMessage_Success() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("FAILED")
                .amount(new BigDecimal("99.99"))
                .message(null)
                .build();

        // When
        emailService.sendOrderNotification(event);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendOrderNotification_MailSenderThrowsException_ThrowsRuntimeException() {
        // Given
        OrderNotificationEvent event = OrderNotificationEvent.builder()
                .orderId(UUID.randomUUID())
                .customerEmail("customer@test.com")
                .status("PAID")
                .amount(new BigDecimal("99.99"))
                .build();

        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        // When/Then
        assertThatThrownBy(() -> emailService.sendOrderNotification(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send email notification");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
