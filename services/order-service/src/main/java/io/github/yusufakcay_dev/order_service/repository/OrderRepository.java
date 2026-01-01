package io.github.yusufakcay_dev.order_service.repository;

import io.github.yusufakcay_dev.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByPaymentId(String paymentId);
}
