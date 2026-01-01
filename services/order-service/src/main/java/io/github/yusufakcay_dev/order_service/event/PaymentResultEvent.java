package io.github.yusufakcay_dev.order_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResultEvent {
    private String paymentId;
    private UUID orderId;
    private String status; // SUCCESS or FAILED
    private String failureReason;
}
