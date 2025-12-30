package io.github.yusufakcay_dev.product_service.repository;

import io.github.yusufakcay_dev.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
}