package io.github.yusufakcay_dev.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductCreatedEvent {
    private String eventId;
    private String sku;
    private Integer initialStock;
    private Long timestamp;
}
