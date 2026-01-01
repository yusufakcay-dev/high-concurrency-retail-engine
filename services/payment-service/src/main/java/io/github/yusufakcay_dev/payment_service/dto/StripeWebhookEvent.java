package io.github.yusufakcay_dev.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simulates Stripe webhook payload for checkout.session.completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeWebhookEvent {
    private String type; // checkout.session.completed or checkout.session.expired
    private StripeData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StripeData {
        private StripeObject object;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StripeObject {
        private String id; // Stripe session/payment ID
        private String paymentStatus; // paid, unpaid
        private String customerEmail;
    }
}
