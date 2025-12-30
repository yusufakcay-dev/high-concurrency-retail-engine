package io.github.yusufakcay_dev.product_service.controller;

import io.github.yusufakcay_dev.product_service.dto.ProductRequest;
import io.github.yusufakcay_dev.product_service.dto.ProductResponse;
import io.github.yusufakcay_dev.product_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService service;

    // POST /products - Protected by gateway (Admin only)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create product", description = "Admin only - validated by gateway")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    public ProductResponse createProduct(
            @RequestBody @Valid ProductRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Name", required = false) String username,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Role", required = false) String role) {

        // Check role first before any service call
        if (role == null || !role.equals("ADMIN")) {
            log.warn("Forbidden: Non-admin user {} attempted product creation", username);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can create products");
        }

        log.info("Creating product with SKU: {} by user: {}", request.getSku(), username);

        ProductResponse response = service.createProduct(request);
        log.info("Product created successfully with ID: {} by {}", response.getId(), username);
        return response;
    }

    // GET /products - Public
    @GetMapping
    @Operation(summary = "Get all products")
    public Page<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {

        Sort sortSpec = Sort.by(
                sort.endsWith(",desc")
                        ? Sort.Order.desc(sort.replace(",desc", ""))
                        : Sort.Order.asc(sort.replace(",asc", "")));
        Pageable pageable = PageRequest.of(page, size, sortSpec);
        return service.getAllProducts(pageable);
    }

    // GET /products/{id} - Public
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get product by ID")
    public ProductResponse getProduct(@PathVariable Long id) {
        return service.getProductById(id);
    }
}