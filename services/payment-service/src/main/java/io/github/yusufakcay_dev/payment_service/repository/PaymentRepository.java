package io.github.yusufakcay_dev.payment_service.repository;

import io.github.yusufakcay_dev.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByStripePaymentId(String stripePaymentId);

    Optional<Payment> findByOrderId(UUID orderId);
}
