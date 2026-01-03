package io.github.yusufakcay_dev.order_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.yusufakcay_dev.order_service.AbstractIntegrationTest;
import io.github.yusufakcay_dev.order_service.dto.CreateOrderRequest;
import io.github.yusufakcay_dev.order_service.dto.OrderItemRequest;
import io.github.yusufakcay_dev.order_service.dto.OrderResponse;
import io.github.yusufakcay_dev.order_service.entity.Order;
import io.github.yusufakcay_dev.order_service.entity.OrderStatus;
import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.repository.OrderRepository;
import io.github.yusufakcay_dev.order_service.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the Order Service saga orchestration.
 * Tests the full order flow: create order -> reserve inventory -> create
 * payment -> handle payment result
 */
@DisplayName("Order Service Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderServiceIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private OrderService orderService;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private StringRedisTemplate redisTemplate;

        private static WireMockServer inventoryMock;
        private static WireMockServer paymentMock;

        @BeforeAll
        static void setupWireMock() {
                inventoryMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
                paymentMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
                inventoryMock.start();
                paymentMock.start();
        }

        @AfterAll
        static void tearDownWireMock() {
                if (inventoryMock != null)
                        inventoryMock.stop();
                if (paymentMock != null)
                        paymentMock.stop();
        }

        @DynamicPropertySource
        static void configureWireMock(DynamicPropertyRegistry registry) {
                registry.add("inventory-service.url", () -> "http://localhost:" + inventoryMock.port());
                registry.add("payment-service.url", () -> "http://localhost:" + paymentMock.port());
        }

        @SuppressWarnings("deprecation")
        @BeforeEach
        void setUp() {
                inventoryMock.resetAll();
                paymentMock.resetAll();
                orderRepository.deleteAll();
                redisTemplate.getConnectionFactory().getConnection().flushAll();
        }

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("Should create order successfully with inventory reservation and payment link")
        void createOrder_Success() {
                // Given: Mock inventory reserve endpoint
                inventoryMock.stubFor(post(urlPathEqualTo("/api/inventories/LAPTOP-001/reserve"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"LAPTOP-001\",\"availableQuantity\":10}")));

                // Given: Mock payment service endpoint
                paymentMock.stubFor(post(urlPathEqualTo("/internal/payments/create-link"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"paymentId\":\"pay_123\",\"paymentUrl\":\"https://checkout.stripe.com/pay_123\"}")));

                CreateOrderRequest request = CreateOrderRequest.builder()
                                .userId(1L)
                                .amount(new BigDecimal("999.99"))
                                .customerEmail("test@example.com")
                                .items(List.of(OrderItemRequest.builder()
                                                .sku("LAPTOP-001")
                                                .quantity(1)
                                                .build()))
                                .build();

                // When
                OrderResponse response = orderService.createOrder(request);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getId()).isNotNull();
                assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(response.getPaymentUrl()).contains("stripe.com");
                assertThat(response.getItems()).hasSize(1);

                // Verify WireMock calls
                inventoryMock.verify(postRequestedFor(urlPathEqualTo("/api/inventories/LAPTOP-001/reserve")));
                paymentMock.verify(postRequestedFor(urlPathEqualTo("/internal/payments/create-link")));

                // Verify order persisted
                Order savedOrder = orderRepository.findById(response.getId()).orElseThrow();
                assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(savedOrder.getPaymentId()).isEqualTo("pay_123");
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("Should mark order as PAID when payment succeeds and confirm inventory")
        void handlePaymentResult_Success() {
                // Given: Create an order first
                inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/reserve"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"MOUSE-001\",\"availableQuantity\":50}")));

                paymentMock.stubFor(post(urlPathEqualTo("/internal/payments/create-link"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"paymentId\":\"pay_456\",\"paymentUrl\":\"https://checkout.stripe.com/pay_456\"}")));

                inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/confirm"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"MOUSE-001\",\"availableQuantity\":49}")));

                CreateOrderRequest request = CreateOrderRequest.builder()
                                .userId(2L)
                                .amount(new BigDecimal("49.99"))
                                .customerEmail("customer@example.com")
                                .items(List.of(OrderItemRequest.builder()
                                                .sku("MOUSE-001")
                                                .quantity(1)
                                                .build()))
                                .build();

                OrderResponse orderResponse = orderService.createOrder(request);

                // When: Handle successful payment result
                PaymentResultEvent paymentSuccess = PaymentResultEvent.builder()
                                .orderId(orderResponse.getId())
                                .paymentId("pay_456")
                                .status("SUCCESS")
                                .build();

                orderService.handlePaymentResult(paymentSuccess);

                // Then: Order should be PAID
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                        Order updatedOrder = orderRepository.findById(orderResponse.getId()).orElseThrow();
                        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
                });

                // Verify inventory confirm was called
                inventoryMock.verify(postRequestedFor(urlPathMatching("/api/inventories/.*/confirm")));
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("Should mark order as FAILED and release inventory when payment fails")
        void handlePaymentResult_Failure_ReleasesInventory() {
                // Given: Create an order
                inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/reserve"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"KEYBOARD-001\",\"availableQuantity\":20}")));

                paymentMock.stubFor(post(urlPathEqualTo("/internal/payments/create-link"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"paymentId\":\"pay_789\",\"paymentUrl\":\"https://checkout.stripe.com/pay_789\"}")));

                inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/release"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"KEYBOARD-001\",\"availableQuantity\":21}")));

                CreateOrderRequest request = CreateOrderRequest.builder()
                                .userId(3L)
                                .amount(new BigDecimal("79.99"))
                                .customerEmail("buyer@example.com")
                                .items(List.of(OrderItemRequest.builder()
                                                .sku("KEYBOARD-001")
                                                .quantity(1)
                                                .build()))
                                .build();

                OrderResponse orderResponse = orderService.createOrder(request);

                // When: Handle failed payment result
                PaymentResultEvent paymentFailed = PaymentResultEvent.builder()
                                .orderId(orderResponse.getId())
                                .paymentId("pay_789")
                                .status("FAILED")
                                .failureReason("Card declined")
                                .build();

                orderService.handlePaymentResult(paymentFailed);

                // Then: Order should be FAILED
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                        Order updatedOrder = orderRepository.findById(orderResponse.getId()).orElseThrow();
                        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
                });

                // Verify inventory release was called (compensation)
                inventoryMock.verify(postRequestedFor(urlPathMatching("/api/inventories/.*/release")));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("Should rollback inventory reservation when payment link creation fails")
        void createOrder_PaymentFails_RollsBackInventory() {
                // Given: Inventory reserve succeeds
                inventoryMock.stubFor(post(urlPathEqualTo("/api/inventories/MONITOR-001/reserve"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"MONITOR-001\",\"availableQuantity\":5}")));

                // Given: Payment service fails
                paymentMock.stubFor(post(urlPathEqualTo("/internal/payments/create-link"))
                                .willReturn(aResponse()
                                                .withStatus(500)
                                                .withBody("Payment service error")));

                // Given: Inventory release endpoint for rollback
                inventoryMock.stubFor(post(urlPathEqualTo("/api/inventories/MONITOR-001/release"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"MONITOR-001\",\"availableQuantity\":6}")));

                CreateOrderRequest request = CreateOrderRequest.builder()
                                .userId(4L)
                                .amount(new BigDecimal("299.99"))
                                .customerEmail("test@example.com")
                                .items(List.of(OrderItemRequest.builder()
                                                .sku("MONITOR-001")
                                                .quantity(1)
                                                .build()))
                                .build();

                // When/Then: Order creation should fail
                try {
                        orderService.createOrder(request);
                } catch (RuntimeException e) {
                        assertThat(e.getMessage()).contains("Failed to create payment link");
                }

                // Verify inventory was reserved then released (rollback)
                inventoryMock.verify(postRequestedFor(urlPathEqualTo("/api/inventories/MONITOR-001/reserve")));
                inventoryMock.verify(postRequestedFor(urlPathEqualTo("/api/inventories/MONITOR-001/release")));
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("Should fetch order by ID")
        void getOrder_Success() {
                // Given: Create an order first
                inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/reserve"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sku\":\"HEADPHONES-001\",\"availableQuantity\":30}")));

                paymentMock.stubFor(post(urlPathEqualTo("/internal/payments/create-link"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"paymentId\":\"pay_fetch\",\"paymentUrl\":\"https://checkout.stripe.com/pay_fetch\"}")));

                CreateOrderRequest request = CreateOrderRequest.builder()
                                .userId(5L)
                                .amount(new BigDecimal("199.99"))
                                .customerEmail("fetch@example.com")
                                .items(List.of(OrderItemRequest.builder()
                                                .sku("HEADPHONES-001")
                                                .quantity(1)
                                                .build()))
                                .build();

                OrderResponse created = orderService.createOrder(request);

                // When: Verify order is persisted correctly by reading from repository
                Order persistedOrder = orderRepository.findById(created.getId()).orElseThrow();

                // Then
                assertThat(persistedOrder).isNotNull();
                assertThat(persistedOrder.getId()).isEqualTo(created.getId());
                assertThat(persistedOrder.getUserId()).isEqualTo(5L);
                assertThat(persistedOrder.getAmount()).isEqualByComparingTo(new BigDecimal("199.99"));
                assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(persistedOrder.getCustomerEmail()).isEqualTo("fetch@example.com");
        }
}
