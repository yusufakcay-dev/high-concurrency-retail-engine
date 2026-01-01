package io.github.yusufakcay_dev.notification_service.service;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Value("${notification.email.from}")
    private String fromEmail;

    /**
     * Mocks sending an email by logging to console
     */
    public void sendOrderNotification(OrderNotificationEvent event) {
        log.info("========================================");
        log.info("📧 SENDING EMAIL (MOCK)");
        log.info("========================================");
        log.info("From: {}", fromEmail);
        log.info("To: {}", event.getCustomerEmail());
        log.info("Subject: Order {} - Payment {}", event.getOrderId(), event.getStatus());
        log.info("----------------------------------------");
        log.info("Body:");
        log.info("");

        if ("PAID".equals(event.getStatus())) {
            log.info("  Dear Customer,");
            log.info("");
            log.info("  Great news! Your payment has been successfully processed.");
            log.info("");
            log.info("  Order Details:");
            log.info("    - Order ID: {}", event.getOrderId());
            log.info("    - Amount: ${}", event.getAmount());
            log.info("    - Status: Payment Successful ✓");
            log.info("");
            log.info("  Thank you for your purchase!");
            log.info("");
            log.info("  Best regards,");
            log.info("  High Concurrency Retail Engine Team");
        } else {
            log.info("  Dear Customer,");
            log.info("");
            log.info("  Unfortunately, your payment could not be processed.");
            log.info("");
            log.info("  Order Details:");
            log.info("    - Order ID: {}", event.getOrderId());
            log.info("    - Amount: ${}", event.getAmount());
            log.info("    - Status: Payment Failed ✗");
            if (event.getMessage() != null) {
                log.info("    - Reason: {}", event.getMessage());
            }
            log.info("");
            log.info("  Please try again or contact our support team.");
            log.info("");
            log.info("  Best regards,");
            log.info("  High Concurrency Retail Engine Team");
        }

        log.info("");
        log.info("========================================");
        log.info("📧 EMAIL SENT SUCCESSFULLY (MOCK)");
        log.info("========================================");
    }
}
