package io.github.yusufakcay_dev.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductCreatedEvent {
    private String eventId; // Add this - UUID
    private String sku;
    private Integer initialStock;
    private Long timestamp;
}