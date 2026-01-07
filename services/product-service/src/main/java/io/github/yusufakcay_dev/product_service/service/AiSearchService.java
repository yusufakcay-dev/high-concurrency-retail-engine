package io.github.yusufakcay_dev.product_service.service;

import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSearchService {

    private final ProductRepository productRepository;
    private final VectorStore vectorStore;

    /**
     * "Trains" (Indexes) the AI by converting all products into vectors.
     * Admin triggers this when data changes or initially.
     */
    @Transactional
    public void indexAllProducts() {
        log.info("Starting AI Indexing...");

        List<Product> products = productRepository.findAll();

        // 1. Convert Products to AI "Documents"
        List<Document> documents = products.stream()
                .map(product -> {
                    // We combine name + description for the AI to "read"
                    String content = "Product: " + product.getName() + ". Description: " + product.getDescription();

                    Map<String, Object> metadata = Map.of("productId", product.getId());

                    return new Document(content, metadata);
                })
                .toList();

        // 2. Clear existing entries in Vector DB
        vectorStore.delete(products.stream().map(p -> p.getId().toString()).toList());

        // 3. Save to Vector DB (This generates embeddings automatically via ONNX)
        vectorStore.add(documents);

        log.info("Indexed {} products into Vector Store.", documents.size());
    }

    /**
     * Semantic Search
     */
    public List<ProductResponse> searchProducts(String query) {
        // 1. Ask Vector Store to find similar documents
        // topK=5 means return top 5 matches
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5).build());

        // 2. Extract Product IDs from the metadata
        List<Long> productIds = similarDocuments.stream()
                .map(doc -> ((Number) doc.getMetadata().get("productId")).longValue())
                .toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        // 3. Fetch Products from DB
        List<Product> products = productRepository.findAllById(productIds);

        return productIds.stream()
                .flatMap(id -> products.stream().filter(p -> p.getId().equals(id)))
                .map(this::mapToResponse)
                .toList();
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .active(product.getActive())
                .inStock(product.getInStock())
                .build();
    }
}