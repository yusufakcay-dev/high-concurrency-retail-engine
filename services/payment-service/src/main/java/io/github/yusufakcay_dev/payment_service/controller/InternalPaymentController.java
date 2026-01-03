package io.github.yusufakcay_dev.payment_service.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.github.yusufakcay_dev.payment_service.dto.PaymentRequest;
import io.github.yusufakcay_dev.payment_service.dto.PaymentResponse;
import io.github.yusufakcay_dev.payment_service.dto.StripeWebhookEvent;
import io.github.yusufakcay_dev.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal controller - NOT exposed through gateway
 * Only Order Service can call these endpoints
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    /**
     * Internal endpoint called by Order Service via Feign Client
     * Creates a Stripe payment link
     */
    @PostMapping("/internal/payments/create-link")
    public ResponseEntity<PaymentResponse> createPaymentLink(@RequestBody PaymentRequest request) {
        log.info("Internal: Creating payment link for order: {}", request.getOrderId());
        PaymentResponse response = paymentService.createPaymentLink(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook endpoint for receiving Stripe events via Gateway
     * Gateway forwards from /payments/webhook to this endpoint
     * 
     * SECURITY: This endpoint REQUIRES valid Stripe signature verification.
     * Requests without valid signatures will be rejected with 400 Bad Request.
     */
    @SuppressWarnings("deprecation")
    @PostMapping({ "/webhooks/stripe", "/payments/webhook" })
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        log.info("Received Stripe webhook request");

        // SECURITY: Reject requests without Stripe-Signature header
        if (sigHeader == null || sigHeader.isEmpty()) {
            log.warn("Webhook request rejected: Missing Stripe-Signature header - possible malicious request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing Stripe-Signature header");
        }

        // SECURITY: Reject if webhook secret is not configured
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.error("Webhook secret not configured - cannot verify signature");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook configuration error");
        }

        Event event;

        try {
            // Verify signature using Stripe SDK - this validates:
            // 1. The signature matches the payload using HMAC-SHA256
            // 2. The timestamp is within tolerance (prevents replay attacks)
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Webhook signature verified successfully for event: {}", event.getId());
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature - possible malicious request. Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid signature - request rejected");
        } catch (Exception e) {
            log.error("Failed to parse webhook event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid event");
        }

        try {
            // Handle checkout.session events (successful payments)
            if (event.getType().startsWith("checkout.session")) {
                // Extract session data using Stripe API
                com.stripe.model.StripeObject stripeObject = event.getData().getObject();

                if (!(stripeObject instanceof Session)) {
                    log.warn("Event data is not a Session object, type: {}", stripeObject.getClass().getName());
                    return ResponseEntity.ok("Unexpected object type");
                }

                Session session = (Session) stripeObject;

                // Convert to our internal DTO
                StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                        .type(event.getType())
                        .data(StripeWebhookEvent.StripeData.builder()
                                .object(StripeWebhookEvent.StripeObject.builder()
                                        .id(session.getId())
                                        .paymentStatus(session.getPaymentStatus())
                                        .customerEmail(session.getCustomerEmail())
                                        .build())
                                .build())
                        .build();

                // Process the webhook
                paymentService.handleStripeWebhook(webhookEvent);

                log.info("Webhook processed successfully: {}", event.getType());
                return ResponseEntity.ok("Webhook processed");
            }
            // Handle payment_intent.payment_failed events
            else if ("payment_intent.payment_failed".equals(event.getType())) {
                com.stripe.model.StripeObject stripeObject = event.getData().getObject();

                if (!(stripeObject instanceof com.stripe.model.PaymentIntent)) {
                    log.warn("Event data is not a PaymentIntent object");
                    return ResponseEntity.ok("Unexpected object type");
                }

                com.stripe.model.PaymentIntent paymentIntent = (com.stripe.model.PaymentIntent) stripeObject;

                // Convert to our internal DTO
                StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                        .type(event.getType())
                        .data(StripeWebhookEvent.StripeData.builder()
                                .object(StripeWebhookEvent.StripeObject.builder()
                                        .id(paymentIntent.getId())
                                        .paymentStatus("failed")
                                        .customerEmail(null)
                                        .build())
                                .build())
                        .build();

                // Process the webhook
                paymentService.handlePaymentIntentFailed(webhookEvent);

                log.info("Payment intent failed webhook processed: {}", paymentIntent.getId());
                return ResponseEntity.ok("Webhook processed");
            } else {
                log.info("Ignoring webhook event type: {}", event.getType());
                return ResponseEntity.ok("Event type ignored");
            }

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Success page - Customer is redirected here after successful Stripe payment
     * Accessible via Gateway at /payments/success
     */
    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentSuccess() {
        log.info("Customer redirected to payment success page");
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>Payment Successful</title>" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; min-height: 100vh; }"
                +
                "        .container { background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 60px 40px; text-align: center; max-width: 500px; }"
                +
                "        .icon { font-size: 80px; margin-bottom: 20px; animation: bounce 1s ease-in-out; }" +
                "        @keyframes bounce { 0%, 20%, 50%, 80%, 100% { transform: translateY(0); } 40% { transform: translateY(-20px); } 60% { transform: translateY(-10px); } }"
                +
                "        h1 { color: #10b981; margin: 0 0 20px; font-size: 32px; }" +
                "        p { color: #6b7280; font-size: 18px; line-height: 1.6; margin: 0; }" +
                "        .note { margin-top: 30px; padding: 20px; background: #f0fdf4; border-left: 4px solid #10b981; border-radius: 8px; color: #065f46; font-size: 16px; }"
                +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='container'>" +
                "        <div class='icon'>✅</div>" +
                "        <h1>Payment Successful!</h1>" +
                "        <p>Thank you for your purchase. Your payment has been processed successfully.</p>" +
                "        <div class='note'>" +
                "            📧 We are processing your order now.<br>" +
                "            You will receive a confirmation email shortly." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * Cancel page - Customer is redirected here when payment is cancelled
     * Accessible via Gateway at /payments/cancel
     */
    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentCancel() {
        log.info("Customer redirected to payment cancel page");
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>Payment Cancelled</title>" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #f87171 0%, #dc2626 100%); margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; min-height: 100vh; }"
                +
                "        .container { background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 60px 40px; text-align: center; max-width: 500px; }"
                +
                "        .icon { font-size: 80px; margin-bottom: 20px; }" +
                "        h1 { color: #ef4444; margin: 0 0 20px; font-size: 32px; }" +
                "        p { color: #6b7280; font-size: 18px; line-height: 1.6; margin: 0; }" +
                "        .note { margin-top: 30px; padding: 20px; background: #fef2f2; border-left: 4px solid #ef4444; border-radius: 8px; color: #991b1b; font-size: 16px; }"
                +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='container'>" +
                "        <div class='icon'>❌</div>" +
                "        <h1>Payment Cancelled</h1>" +
                "        <p>Your payment has been cancelled. No charges have been made to your account.</p>" +
                "        <div class='note'>" +
                "            If you encountered any issues or need assistance,<br>" +
                "            please contact our support team." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }
}
