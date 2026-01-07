package io.github.yusufakcay_dev.product_service.controller;

import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.service.AiSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI Search", description = "Semantic Search with pgvector")
public class AiController {

    private final AiSearchService aiService;

    // Endpoint 1: "Train" (Index)
    @PostMapping("/index")
    @Operation(summary = "Admin: Index products for AI", description = "Generates embeddings for all products")
    public String indexProducts() {
        aiService.indexAllProducts();
        return "AI Indexing triggered successfully.";
    }

    // Endpoint 2: Search
    @GetMapping("/search")
    @Operation(summary = "Semantic Search", description = "Search products by meaning (e.g., 'something to run in')")
    public List<ProductResponse> search(@RequestParam String query) {
        return aiService.searchProducts(query);
    }
}