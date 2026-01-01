package io.github.yusufakcay_dev.product_service.repository;

import io.github.yusufakcay_dev.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);
}