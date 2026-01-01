package io.github.yusufakcay_dev.payment_service.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.github.yusufakcay_dev.payment_service.dto.PaymentRequest;
import io.github.yusufakcay_dev.payment_service.dto.PaymentResponse;
import io.github.yusufakcay_dev.payment_service.dto.StripeWebhookEvent;
import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import io.github.yusufakcay_dev.payment_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.payment_service.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYMENT_RESULT_TOPIC = "payment-results";

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API initialized");
    }

    /**
     * Creates a Stripe checkout session
     * Called internally by Order Service via Feign
     */
    @Transactional
    public PaymentResponse createPaymentLink(PaymentRequest request) {
        log.info("Creating Stripe checkout session for order: {}, amount: {}", request.getOrderId(),
                request.getAmount());

        try {
            // Create Stripe checkout session
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .setCustomerEmail(request.getCustomerEmail())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(request.getCurrency().toLowerCase())
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Order #" + request.getOrderId())
                                                                    .build())
                                                    // Convert dollars to cents (Stripe requires amount in smallest
                                                    // currency unit)
                                                    .setUnitAmount(request.getAmount().movePointRight(2).longValue())
                                                    .build())
                                    .setQuantity(1L)
                                    .build())
                    .putMetadata("orderId", request.getOrderId().toString())
                    .build();

            Session session = Session.create(params);

            // Save payment record
            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .amount(request.getAmount())
                    .email(request.getCustomerEmail())
                    .customerEmail(request.getCustomerEmail())
                    .currency(request.getCurrency())
                    .status(PaymentStatus.PENDING)
                    .stripePaymentId(session.getId())
                    .stripePaymentUrl(session.getUrl())
                    .build();

            payment = paymentRepository.save(payment);
            log.info("Payment record created with ID: {}, Stripe Session ID: {}", payment.getId(), session.getId());

            return PaymentResponse.builder()
                    .paymentId(payment.getId().toString())
                    .paymentUrl(session.getUrl())
                    .status(payment.getStatus().name())
                    .build();

        } catch (StripeException e) {
            log.error("Failed to create Stripe checkout session", e);
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    /**
     * Handles Stripe webhook callback
     * Updates payment status and publishes event to Kafka for Order Service
     */
    @Transactional
    public void handleStripeWebhook(StripeWebhookEvent event) {
        log.info("Received Stripe webhook: type={}", event.getType());

        String stripePaymentId = event.getData().getObject().getId();
        Payment payment = paymentRepository.findByStripePaymentId(stripePaymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for Stripe ID: " + stripePaymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Payment {} already processed, skipping", payment.getId());
            return;
        }

        PaymentResultEvent resultEvent;

        if ("checkout.session.completed".equals(event.getType()) &&
                "paid".equals(event.getData().getObject().getPaymentStatus())) {
            // Payment successful
            payment.setStatus(PaymentStatus.SUCCESS);
            log.info("Payment {} marked as SUCCESS", payment.getId());

            resultEvent = PaymentResultEvent.builder()
                    .paymentId(payment.getId().toString())
                    .orderId(payment.getOrderId())
                    .status("SUCCESS")
                    .build();

        } else {
            // Payment failed or expired
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment " + event.getType());
            log.info("Payment {} marked as FAILED", payment.getId());

            resultEvent = PaymentResultEvent.builder()
                    .paymentId(payment.getId().toString())
                    .orderId(payment.getOrderId())
                    .status("FAILED")
                    .failureReason("Payment " + event.getType())
                    .build();
        }

        paymentRepository.save(payment);

        // Publish to Kafka for Order Service to consume
        kafkaTemplate.send(PAYMENT_RESULT_TOPIC, payment.getOrderId().toString(), resultEvent);
        log.info("Payment result event published to Kafka for order: {}", payment.getOrderId());
    }

    /**
     * Handle payment_intent.payment_failed webhook events
     * When a card is declined during checkout, Stripe sends this event
     */
    @Transactional
    public void handlePaymentIntentFailed(StripeWebhookEvent event) {
        log.info("Received payment_intent.payment_failed webhook");

        String paymentIntentId = event.getData().getObject().getId();

        Payment payment = null;

        try {
            // Retrieve the PaymentIntent from Stripe to get the checkout session ID
            com.stripe.model.PaymentIntent paymentIntent = com.stripe.model.PaymentIntent.retrieve(paymentIntentId);

            // The PaymentIntent has metadata or you can find it via the latest_charge
            // Look for the checkout session in the PaymentIntent's metadata or invoice
            if (paymentIntent.getMetadata() != null && paymentIntent.getMetadata().containsKey("orderId")) {
                String orderId = paymentIntent.getMetadata().get("orderId");
                log.info("Found orderId in PaymentIntent metadata: {}", orderId);
                payment = paymentRepository.findByOrderId(UUID.fromString(orderId))
                        .orElse(null);
            }

            // If not found via metadata, try to find by the latest charge's invoice or
            // receipt
            // Alternatively, search for any pending payment (last resort)
            if (payment == null) {
                log.warn("Could not find payment by metadata, searching for latest pending payment");
                payment = paymentRepository.findAll().stream()
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                        .max((p1, p2) -> p1.getCreatedAt().compareTo(p2.getCreatedAt()))
                        .orElse(null);
            }

        } catch (Exception e) {
            log.error("Error retrieving PaymentIntent from Stripe: {}", e.getMessage());
            // Fallback to finding latest pending payment
            payment = paymentRepository.findAll().stream()
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                    .max((p1, p2) -> p1.getCreatedAt().compareTo(p2.getCreatedAt()))
                    .orElse(null);
        }

        if (payment == null) {
            log.error("No pending payment found for PaymentIntent: {}", paymentIntentId);
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Payment {} already processed, skipping", payment.getId());
            return;
        }

        // Mark payment as failed
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Card declined or payment failed");
        log.info("Payment {} marked as FAILED due to payment_intent.payment_failed", payment.getId());

        paymentRepository.save(payment);

        // Publish failure event to Kafka
        PaymentResultEvent resultEvent = PaymentResultEvent.builder()
                .paymentId(payment.getId().toString())
                .orderId(payment.getOrderId())
                .status("FAILED")
                .failureReason("Card declined or payment failed")
                .build();

        kafkaTemplate.send(PAYMENT_RESULT_TOPIC, payment.getOrderId().toString(), resultEvent);
        log.info("Payment failure event published to Kafka for order: {}", payment.getOrderId());
    }
}
