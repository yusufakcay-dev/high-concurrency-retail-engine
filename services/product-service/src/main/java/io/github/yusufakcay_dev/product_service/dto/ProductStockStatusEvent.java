package io.github.yusufakcay_dev.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockStatusEvent {
    private String sku;
    private Boolean inStock;
}
