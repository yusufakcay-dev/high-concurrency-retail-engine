package io.github.yusufakcay_dev.order_service.client;

import io.github.yusufakcay_dev.order_service.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", url = "${inventory-service.url}", fallback = InventoryServiceFallback.class)
public interface InventoryServiceClient {

    @PostMapping("/api/inventories/{sku}/reserve")
    InventoryResponse reserve(@PathVariable("sku") String sku, @RequestParam("quantity") Integer quantity);

    @PostMapping("/api/inventories/{sku}/release")
    InventoryResponse release(@PathVariable("sku") String sku, @RequestParam("quantity") Integer quantity);

    @PostMapping("/api/inventories/{sku}/confirm")
    InventoryResponse confirm(@PathVariable("sku") String sku, @RequestParam("quantity") Integer quantity);
}
