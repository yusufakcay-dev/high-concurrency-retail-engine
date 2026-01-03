package io.github.yusufakcay_dev.inventory_service.controller;

import io.github.yusufakcay_dev.inventory_service.dto.InventoryResponse;
import io.github.yusufakcay_dev.inventory_service.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController).build();
    }

    @Test
    void testGetInventorySuccess() throws Exception {
        String sku = "TEST-SKU-001";
        InventoryResponse response = InventoryResponse.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .build();

        when(inventoryService.getInventoryBySku(sku)).thenReturn(response);

        mockMvc.perform(get("/inventories/{sku}", sku)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.availableQuantity").value(90));

        verify(inventoryService).getInventoryBySku(sku);
    }

    @Test
    void testGetInventoryNotFound() throws Exception {
        String sku = "NON-EXISTENT";
        when(inventoryService.getInventoryBySku(sku))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));

        mockMvc.perform(get("/inventories/{sku}", sku)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testReserveInventorySuccess() throws Exception {
        String sku = "TEST-SKU-002";
        InventoryResponse response = InventoryResponse.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(40)
                .availableQuantity(60)
                .build();

        when(inventoryService.reserveInventory(sku, 30)).thenReturn(response);

        mockMvc.perform(post("/inventories/{sku}/reserve", sku)
                .param("quantity", "30")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedQuantity").value(40))
                .andExpect(jsonPath("$.availableQuantity").value(60));
    }

    @Test
    void testReleaseInventorySuccess() throws Exception {
        String sku = "TEST-SKU-003";
        InventoryResponse response = InventoryResponse.builder()
                .sku(sku)
                .quantity(100)
                .reservedQuantity(35)
                .availableQuantity(65)
                .build();

        when(inventoryService.releaseReservedInventory(sku, 15)).thenReturn(response);

        mockMvc.perform(post("/inventories/{sku}/release", sku)
                .param("quantity", "15")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQuantity").value(35));
    }
}
