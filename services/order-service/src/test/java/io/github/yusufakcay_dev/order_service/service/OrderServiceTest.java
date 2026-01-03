package io.github.yusufakcay_dev.order_service.service;

import io.github.yusufakcay_dev.order_service.client.InventoryServiceClient;
import io.github.yusufakcay_dev.order_service.client.PaymentServiceClient;
import io.github.yusufakcay_dev.order_service.dto.*;
import io.github.yusufakcay_dev.order_service.entity.Order;
import io.github.yusufakcay_dev.order_service.entity.OrderItem;
import io.github.yusufakcay_dev.order_service.entity.OrderStatus;
import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService using Mockito.
 * Tests business logic in isolation without external dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private PaymentServiceClient paymentServiceClient;

        @Mock
        private InventoryServiceClient inventoryServiceClient;

        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @InjectMocks
        private OrderService orderService;

        private CreateOrderRequest validRequest;
        private InventoryResponse inventoryResponse;
        private PaymentResponse paymentResponse;
        private Order savedOrder;

        @BeforeEach
        void setUp() {
                validRequest = CreateOrderRequest.builder()
                                .userId(1L)
                                .amount(new BigDecimal("100.00"))
                                .customerEmail("test@example.com")
                                .items(List.of(
                                                OrderItemRequest.builder()
                                                                .sku("TEST-SKU")
                                                                .quantity(2)
                                                                .build()))
                                .build();

                inventoryResponse = InventoryResponse.builder()
                                .sku("TEST-SKU")
                                .availableQuantity(10)
                                .build();

                paymentResponse = PaymentResponse.builder()
                                .paymentId("pay_test_123")
                                .paymentUrl("https://checkout.stripe.com/test")
                                .build();

                savedOrder = Order.builder()
                                .id(UUID.randomUUID())
                                .userId(1L)
                                .amount(new BigDecimal("100.00"))
                                .status(OrderStatus.PENDING)
                                .customerEmail("test@example.com")
                                .paymentId("pay_test_123")
                                .paymentUrl("https://checkout.stripe.com/test")
                                .build();

                OrderItem item = OrderItem.builder()
                                .id(UUID.randomUUID())
                                .sku("TEST-SKU")
                                .quantity(2)
                                .build();
                item.setOrder(savedOrder);
                savedOrder.getItems().add(item);
        }

        @Test
        @DisplayName("Should create order successfully")
        void createOrder_Success() {
                // Given
                when(inventoryServiceClient.reserve(anyString(), anyInt())).thenReturn(inventoryResponse);
                when(paymentServiceClient.createPaymentLink(any(PaymentRequest.class))).thenReturn(paymentResponse);
                when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

                // When
                OrderResponse response = orderService.createOrder(validRequest);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(savedOrder.getId());
                assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(response.getPaymentUrl()).contains("stripe.com");

                verify(inventoryServiceClient).reserve("TEST-SKU", 2);
                verify(paymentServiceClient).createPaymentLink(any(PaymentRequest.class));
                verify(orderRepository, times(2)).save(any(Order.class));
        }

        @Test
        @DisplayName("Should rollback inventory when payment fails")
        void createOrder_PaymentFails_RollbacksInventory() {
                // Given
                when(inventoryServiceClient.reserve(anyString(), anyInt())).thenReturn(inventoryResponse);
                when(paymentServiceClient.createPaymentLink(any(PaymentRequest.class)))
                                .thenThrow(new RuntimeException("Payment service error"));
                when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

                // When/Then
                assertThatThrownBy(() -> orderService.createOrder(validRequest))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Failed to create payment link");

                verify(inventoryServiceClient).reserve("TEST-SKU", 2);
                verify(inventoryServiceClient).release("TEST-SKU", 2); // Rollback
        }

        @Test
        @DisplayName("Should handle successful payment result")
        void handlePaymentResult_Success() {
                // Given
                Order pendingOrder = Order.builder()
                                .id(UUID.randomUUID())
                                .userId(1L)
                                .amount(new BigDecimal("100.00"))
                                .status(OrderStatus.PENDING)
                                .customerEmail("test@example.com")
                                .build();

                OrderItem item = OrderItem.builder()
                                .sku("TEST-SKU")
                                .quantity(1)
                                .build();
                item.setOrder(pendingOrder);
                pendingOrder.getItems().add(item);

                PaymentResultEvent event = PaymentResultEvent.builder()
                                .orderId(pendingOrder.getId())
                                .paymentId("pay_123")
                                .status("SUCCESS")
                                .build();

                when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
                when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);

                // When
                orderService.handlePaymentResult(event);

                // Then
                assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);
                verify(inventoryServiceClient).confirm("TEST-SKU", 1);
                verify(kafkaTemplate).send(eq("order-notifications"), anyString(), any());
        }

        @Test
        @DisplayName("Should handle failed payment result and release inventory")
        void handlePaymentResult_Failure() {
                // Given
                Order pendingOrder = Order.builder()
                                .id(UUID.randomUUID())
                                .userId(1L)
                                .amount(new BigDecimal("100.00"))
                                .status(OrderStatus.PENDING)
                                .customerEmail("test@example.com")
                                .build();

                OrderItem item = OrderItem.builder()
                                .sku("TEST-SKU")
                                .quantity(1)
                                .build();
                item.setOrder(pendingOrder);
                pendingOrder.getItems().add(item);

                PaymentResultEvent event = PaymentResultEvent.builder()
                                .orderId(pendingOrder.getId())
                                .paymentId("pay_123")
                                .status("FAILED")
                                .failureReason("Card declined")
                                .build();

                when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
                when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);

                // When
                orderService.handlePaymentResult(event);

                // Then
                assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
                verify(inventoryServiceClient).release("TEST-SKU", 1); // Compensation
                verify(kafkaTemplate).send(eq("order-notifications"), anyString(), any());
        }

        @Test
        @DisplayName("Should skip payment result for non-PENDING order")
        void handlePaymentResult_SkipsNonPendingOrder() {
                // Given
                Order paidOrder = Order.builder()
                                .id(UUID.randomUUID())
                                .status(OrderStatus.PAID)
                                .build();

                PaymentResultEvent event = PaymentResultEvent.builder()
                                .orderId(paidOrder.getId())
                                .status("SUCCESS")
                                .build();

                when(orderRepository.findById(paidOrder.getId())).thenReturn(Optional.of(paidOrder));

                // When
                orderService.handlePaymentResult(event);

                // Then
                verify(inventoryServiceClient, never()).confirm(anyString(), anyInt());
                verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should get order by ID")
        void getOrder_Success() {
                // Given
                when(orderRepository.findById(savedOrder.getId())).thenReturn(Optional.of(savedOrder));

                // When
                OrderResponse response = orderService.getOrder(savedOrder.getId());

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(savedOrder.getId());
                assertThat(response.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw exception when order not found")
        void getOrder_NotFound() {
                // Given
                UUID orderId = UUID.randomUUID();
                when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> orderService.getOrder(orderId))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Order not found");
        }
}
