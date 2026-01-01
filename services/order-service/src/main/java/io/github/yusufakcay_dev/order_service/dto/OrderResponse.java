package io.github.yusufakcay_dev.order_service.dto;

import io.github.yusufakcay_dev.order_service.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private UUID id;
    private Long userId;
    private BigDecimal amount;
    private OrderStatus status;
    private String paymentUrl;
    private String customerEmail;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
}
