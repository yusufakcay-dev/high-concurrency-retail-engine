package io.github.yusufakcay_dev.order_service.service;

import io.github.yusufakcay_dev.order_service.client.InventoryServiceClient;
import io.github.yusufakcay_dev.order_service.client.PaymentServiceClient;
import io.github.yusufakcay_dev.order_service.dto.*;
import io.github.yusufakcay_dev.order_service.entity.Order;
import io.github.yusufakcay_dev.order_service.entity.OrderItem;
import io.github.yusufakcay_dev.order_service.entity.OrderStatus;
import io.github.yusufakcay_dev.order_service.event.OrderNotificationEvent;
import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_NOTIFICATION_TOPIC = "order-notifications";

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());

        // 1. Reserve inventory for all items
        List<OrderItemRequest> reservedItems = new ArrayList<>();
        try {
            for (OrderItemRequest item : request.getItems()) {
                log.info("Reserving {} units for SKU: {}", item.getQuantity(), item.getSku());
                inventoryServiceClient.reserve(item.getSku(), item.getQuantity());
                reservedItems.add(item);
            }
        } catch (Exception e) {
            log.error("Failed to reserve inventory: {}", e.getMessage());
            // Rollback already reserved items
            releaseReservedItems(reservedItems);
            throw new RuntimeException("Failed to reserve inventory: " + e.getMessage());
        }

        // 2. Create order entity with PENDING status
        Order order = Order.builder()
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .customerEmail(request.getCustomerEmail())
                .build();

        // Add order items
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .sku(itemRequest.getSku())
                    .quantity(itemRequest.getQuantity())
                    .build();
            order.addItem(orderItem);
        }

        order = orderRepository.save(order);
        log.info("Order created with ID: {}", order.getId());

        // 3. Call Payment Service to generate Stripe link (Sync via Feign)
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(order.getId())
                .amount(request.getAmount())
                .customerEmail(request.getCustomerEmail())
                .currency("USD")
                .build();

        try {
            PaymentResponse paymentResponse = paymentServiceClient.createPaymentLink(paymentRequest);
            log.info("Payment link created: {}", paymentResponse.getPaymentUrl());

            // 4. Update order with payment info
            order.setPaymentId(paymentResponse.getPaymentId());
            order.setPaymentUrl(paymentResponse.getPaymentUrl());
            order = orderRepository.save(order);

            return mapToResponse(order);

        } catch (Exception e) {
            log.error("Failed to create payment link for order: {}", order.getId(), e);
            // Release all reserved inventory
            releaseReservedItems(request.getItems());
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            throw new RuntimeException("Failed to create payment link: " + e.getMessage());
        }
    }

    public OrderResponse getOrder(UUID orderId) {
        log.info("Fetching order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        return mapToResponse(order);
    }

    @Transactional
    public void handlePaymentResult(PaymentResultEvent event) {
        log.info("Handling payment result for order: {}, status: {}", event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} is not in PENDING status, skipping update", order.getId());
            return;
        }

        // Update order status based on payment result
        if ("SUCCESS".equals(event.getStatus())) {
            order.setStatus(OrderStatus.PAID);
            log.info("Order {} marked as PAID", order.getId());

            // Confirm inventory reservation (decrease actual stock, release reserve)
            for (OrderItem item : order.getItems()) {
                try {
                    inventoryServiceClient.confirm(item.getSku(), item.getQuantity());
                    log.info("Confirmed {} units for SKU: {}", item.getQuantity(), item.getSku());
                } catch (Exception e) {
                    log.error("Failed to confirm inventory for SKU: {}. Manual intervention required.", item.getSku(),
                            e);
                }
            }
        } else {
            order.setStatus(OrderStatus.FAILED);
            log.info("Order {} marked as FAILED", order.getId());

            // Release reserved inventory
            for (OrderItem item : order.getItems()) {
                try {
                    inventoryServiceClient.release(item.getSku(), item.getQuantity());
                    log.info("Released {} units for SKU: {}", item.getQuantity(), item.getSku());
                } catch (Exception e) {
                    log.error("Failed to release inventory for SKU: {}. Manual intervention required.", item.getSku(),
                            e);
                }
            }
        }

        orderRepository.save(order);

        // Publish notification event for Notification Service
        OrderNotificationEvent notificationEvent = OrderNotificationEvent.builder()
                .orderId(order.getId())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus().name())
                .amount(order.getAmount())
                .message(order.getStatus() == OrderStatus.PAID
                        ? "Your payment was successful! Thank you for your order."
                        : "Your payment failed. Please try again. Reason: " + event.getFailureReason())
                .build();

        kafkaTemplate.send(ORDER_NOTIFICATION_TOPIC, order.getId().toString(), notificationEvent);
        log.info("Notification event published for order: {}", order.getId());
    }

    private void releaseReservedItems(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            try {
                inventoryServiceClient.release(item.getSku(), item.getQuantity());
                log.info("Released {} units for SKU: {} during rollback", item.getQuantity(), item.getSku());
            } catch (Exception e) {
                log.error("Failed to release inventory for SKU: {} during rollback", item.getSku(), e);
            }
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .paymentUrl(order.getPaymentUrl())
                .customerEmail(order.getCustomerEmail())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
