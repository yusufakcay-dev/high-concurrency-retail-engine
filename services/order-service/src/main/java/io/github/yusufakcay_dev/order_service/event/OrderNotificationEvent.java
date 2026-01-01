package io.github.yusufakcay_dev.order_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderNotificationEvent {
    private UUID orderId;
    private String customerEmail;
    private String status; // PAID or FAILED
    private BigDecimal amount;
    private String message;
}
