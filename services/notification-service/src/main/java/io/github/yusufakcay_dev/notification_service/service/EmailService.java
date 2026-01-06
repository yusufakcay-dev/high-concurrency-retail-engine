package io.github.yusufakcay_dev.notification_service.service;

import io.github.yusufakcay_dev.notification_service.event.OrderNotificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.from}")
    private String fromEmail;

    /**
     * Sends order notification email via Mailpit SMTP with HTML template
     */
    public void sendOrderNotification(OrderNotificationEvent event) {
        log.info("========================================");
        log.info("📧 SENDING HTML EMAIL via Mailpit");
        log.info("========================================");
        log.info("From: {}", fromEmail);
        log.info("To: {}", event.getCustomerEmail());
        log.info("Subject: Order {} - Payment {}", event.getOrderId(), event.getStatus());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(event.getCustomerEmail());
            helper.setSubject(String.format("Order %s - Payment %s", event.getOrderId(), event.getStatus()));
            helper.setText(buildHtmlEmailBody(event), true); // true = isHtml

            mailSender.send(message);

            log.info("✓ HTML Email sent successfully to Mailpit!");
            log.info("🌐 View email at: https://mailpit.yusufakcay.dev");
            log.info("========================================");
        } catch (MessagingException e) {
            log.error("✗ Failed to send email via Mailpit", e);
            throw new RuntimeException("Failed to send email notification", e);
        } catch (Exception e) {
            log.error("✗ Failed to send email via Mailpit", e);
            throw new RuntimeException("Failed to send email notification", e);
        }
    }

    private String buildHtmlEmailBody(OrderNotificationEvent event) {
        boolean isPaid = "PAID".equals(event.getStatus());
        String statusColor = isPaid ? "#10b981" : "#ef4444";
        String statusIcon = isPaid ? "✓" : "✗";
        String statusText = isPaid ? "Payment Successful" : "Payment Failed";

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Order Notification</title>
                        </head>
                        <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f3f4f6;">
                            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f3f4f6; padding: 40px 20px;">
                                <tr>
                                    <td align="center">
                                        <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                            <!-- Header -->
                                            <tr>
                                                <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px 40px 30px; border-radius: 8px 8px 0 0;">
                                                    <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 600; text-align: center;">
                                                        🛒 High Concurrency Retail
                                                    </h1>
                                                </td>
                                            </tr>

                                            <!-- Status Badge -->
                                            <tr>
                                                <td style="padding: 30px 40px 20px; text-align: center;">
                                                    <div style="display: inline-block; background-color: %s; color: #ffffff; padding: 12px 24px; border-radius: 25px; font-size: 16px; font-weight: 600;">
                                                        %s %s
                                                    </div>
                                                </td>
                                            </tr>

                                            <!-- Content -->
                                            <tr>
                                                <td style="padding: 20px 40px;">
                                                    <h2 style="margin: 0 0 20px; color: #1f2937; font-size: 22px;">
                                                        Dear Customer,
                                                    </h2>
                                                    <p style="margin: 0 0 20px; color: #4b5563; font-size: 16px; line-height: 1.6;">
                                                        %s
                                                    </p>
                                                </td>
                                            </tr>

                                            <!-- Order Details Box -->
                                            <tr>
                                                <td style="padding: 0 40px 30px;">
                                                    <div style="background-color: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 24px;">
                                                        <h3 style="margin: 0 0 16px; color: #1f2937; font-size: 18px; font-weight: 600;">
                                                            📦 Order Details
                                                        </h3>
                                                        <table width="100%%" cellpadding="8" cellspacing="0">
                                                            <tr>
                                                                <td style="color: #6b7280; font-size: 14px; padding: 8px 0;">Order ID:</td>
                                                                <td style="color: #1f2937; font-size: 14px; font-weight: 600; text-align: right; padding: 8px 0;">%s</td>
                                                            </tr>
                                                            <tr>
                                                                <td style="color: #6b7280; font-size: 14px; padding: 8px 0; border-top: 1px solid #e5e7eb;">Amount:</td>
                                                                <td style="color: #1f2937; font-size: 14px; font-weight: 600; text-align: right; padding: 8px 0; border-top: 1px solid #e5e7eb;">$%s</td>
                                                            </tr>
                                                            <tr>
                                                                <td style="color: #6b7280; font-size: 14px; padding: 8px 0; border-top: 1px solid #e5e7eb;">Status:</td>
                                                                <td style="color: %s; font-size: 14px; font-weight: 600; text-align: right; padding: 8px 0; border-top: 1px solid #e5e7eb;">%s %s</td>
                                                            </tr>
                                                            %s
                                                        </table>
                                                    </div>
                                                </td>
                                            </tr>

                                            <!-- Message -->
                                            <tr>
                                                <td style="padding: 0 40px 30px;">
                                                    <p style="margin: 0; color: #4b5563; font-size: 15px; line-height: 1.6;">
                                                        %s
                                                    </p>
                                                </td>
                                            </tr>

                                            <!-- Footer -->
                                            <tr>
                                                <td style="background-color: #f9fafb; padding: 30px 40px; border-radius: 0 0 8px 8px; border-top: 1px solid #e5e7eb;">
                                                    <p style="margin: 0 0 10px; color: #6b7280; font-size: 14px; text-align: center;">
                                                        Best regards,<br>
                                                        <strong style="color: #1f2937;">High Concurrency Retail Engine Team</strong>
                                                    </p>
                                                    <p style="margin: 15px 0 0; color: #9ca3af; font-size: 12px; text-align: center; line-height: 1.5;">
                                                        This is an automated message. Please do not reply to this email.<br>
                                                        © 2026 High Concurrency Retail Engine. All rights reserved.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </body>
                        </html>
                        """,
                statusColor, // Badge background color
                statusIcon, // Badge icon
                statusText, // Badge text
                isPaid
                        ? "Great news! Your payment has been successfully processed. Your order is now being prepared for shipment."
                        : "Unfortunately, your payment could not be processed. Please review the details below and try again.",
                event.getOrderId(), // Order ID
                event.getAmount(), // Amount
                statusColor, // Status color
                statusIcon, // Status icon
                statusText, // Status text
                event.getMessage() != null && !isPaid
                        ? String.format(
                                """
                                        <tr>
                                            <td style="color: #6b7280; font-size: 14px; padding: 8px 0; border-top: 1px solid #e5e7eb;">Reason:</td>
                                            <td style="color: #ef4444; font-size: 14px; font-weight: 500; text-align: right; padding: 8px 0; border-top: 1px solid #e5e7eb;">%s</td>
                                        </tr>
                                        """,
                                event.getMessage())
                        : "",
                isPaid
                        ? "Thank you for shopping with us! You will receive a shipping confirmation once your order has been dispatched."
                        : "If you continue to experience issues, please contact our support team for assistance.");
    }
}
