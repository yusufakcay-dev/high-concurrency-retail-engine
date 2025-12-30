package io.github.yusufakcay_dev.inventory_service.controller;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String sku) {
        InventoryResponse response = inventoryService.getInventoryBySku(sku);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sku}/reserve")
    public ResponseEntity<InventoryResponse> reserve(
            @PathVariable String sku,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.reserveInventory(sku, quantity);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sku}/release")
    public ResponseEntity<InventoryResponse> release(
            @PathVariable String sku,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.releaseReservedInventory(sku, quantity);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sku}/confirm")
    public ResponseEntity<InventoryResponse> confirm(
            @PathVariable String sku,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.confirmReservation(sku, quantity);
        return ResponseEntity.ok(response);
    }

}
