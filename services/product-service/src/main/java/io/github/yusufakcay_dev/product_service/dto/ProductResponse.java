package io.github.yusufakcay_dev.product_service.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private Boolean active;
}