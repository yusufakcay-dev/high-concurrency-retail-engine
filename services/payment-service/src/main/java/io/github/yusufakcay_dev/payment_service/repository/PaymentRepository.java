package io.github.yusufakcay_dev.payment_service.repository;

import io.github.yusufakcay_dev.payment_service.entity.Payment;
import io.github.yusufakcay_dev.payment_service.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByStripePaymentId(String stripePaymentId);

    Optional<Payment> findByOrderId(UUID orderId);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :expirationTime")
    List<Payment> findByStatusAndCreatedAtBefore(
            @Param("status") PaymentStatus status,
            @Param("expirationTime") LocalDateTime expirationTime);
}
