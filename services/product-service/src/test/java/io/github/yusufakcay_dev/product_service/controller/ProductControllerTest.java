package io.github.yusufakcay_dev.product_service.controller;

import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private ProductService service;

        @Test
        void testGetAllProductsSuccess() throws Exception {
                ProductResponse p1 = ProductResponse.builder()
                                .id(1L).name("P1").sku("SKU1").price(BigDecimal.TEN).active(true).build();
                Page<ProductResponse> page = new PageImpl<>(Arrays.asList(p1), PageRequest.of(0, 10), 1);
                when(service.getAllProducts(any())).thenReturn(page);

                mockMvc.perform(get("/products?page=0&size=10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].sku").value("SKU1"));
        }

        @Test
        void testGetProductByIdNotFound() throws Exception {
                when(service.getProductById(999L))
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

                mockMvc.perform(get("/products/999"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void testCreateProductSuccess() throws Exception {
                ProductResponse response = ProductResponse.builder()
                                .id(1L).name("New").sku("SKU999").price(BigDecimal.valueOf(99)).active(true).build();
                when(service.createProduct(any())).thenReturn(response);

                mockMvc.perform(post("/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Name", "admin-user")
                                .header("X-User-Role", "ADMIN")
                                .content("{\"name\":\"New\",\"sku\":\"SKU999\",\"price\":99.00,\"initialStock\":10}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.sku").value("SKU999"));
        }

        @Test
        void testCreateProductForbidden() throws Exception {
                mockMvc.perform(post("/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Name", "regular-user")
                                .header("X-User-Role", "USER")
                                .content("{\"name\":\"New\",\"sku\":\"SKU999\",\"price\":99.00,\"initialStock\":10}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void testCreateProductValidationError() throws Exception {
                mockMvc.perform(post("/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Name", "admin-user")
                                .header("X-User-Role", "ADMIN")
                                .content("{\"name\":\"\",\"sku\":\"SKU999\",\"price\":-1,\"initialStock\":10}"))
                                .andExpect(status().isBadRequest());
        }
}