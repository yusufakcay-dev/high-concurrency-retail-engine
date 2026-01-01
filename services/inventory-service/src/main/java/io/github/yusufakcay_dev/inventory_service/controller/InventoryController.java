package io.github.yusufakcay_dev.inventory_service.controller;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.dto.UpdateInventoryRequest;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventories")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{sku}")
    @Operation(summary = "Get inventory by SKU", description = "Retrieve inventory details for a specific SKU")
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
    @Operation(summary = "Confirm reservation", description = "Confirm reserved inventory and reduce stock")
    public ResponseEntity<InventoryResponse> confirm(
            @PathVariable String sku,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.confirmReservation(sku, quantity);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{sku}")
    @Operation(summary = "Update inventory quantity", description = "Update inventory quantity for a specific SKU (ADMIN only)")
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable String sku,
            @Valid @RequestBody UpdateInventoryRequest request) {
        InventoryResponse response = inventoryService.updateInventoryQuantity(sku, request.getQuantity());
        return ResponseEntity.ok(response);
    }

}
