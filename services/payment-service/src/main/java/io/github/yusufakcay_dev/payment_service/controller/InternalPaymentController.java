package io.github.yusufakcay_dev.payment_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * Webhook endpoint for receiving Stripe events
     * Use Stripe CLI for local testing: stripe listen --forward-to
     * localhost:8085/webhooks/stripe
     */
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        log.info("Received Stripe webhook");

        Event event;

        try {
            // Always verify signature if secret is configured
            if (webhookSecret != null && !webhookSecret.isEmpty() && sigHeader != null) {
                // Verify signature and parse event
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
                log.info("Webhook signature verified successfully");
            } else {
                // For development/testing without signature
                log.warn("Processing webhook without signature verification");
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            }
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
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
}
