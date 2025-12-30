package io.github.yusufakcay_dev.product_service;

import io.github.yusufakcay_dev.product_service.config.TestKafkaConfig;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestKafkaConfig.class)
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository repository;

    @Test
    void testCreateAndRetrieveProduct() throws Exception {
        String json = "{\"name\":\"Integration Test\",\"sku\":\"INT-TEST\",\"price\":50.00,\"initialStock\":5}";

        mockMvc.perform(post("/products")
                .header("X-User-Name", "admin-user")
                .header("X-User-Role", "ADMIN")
                .contentType("application/json")
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("INT-TEST"));

        Product saved = repository.findAll().stream()
                .filter(p -> p.getSku().equals("INT-TEST"))
                .findFirst()
                .orElse(null);

        assertNotNull(saved);
        assertEquals("Integration Test", saved.getName());
    }
}