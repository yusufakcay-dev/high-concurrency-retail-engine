package io.github.yusufakcay_dev.order_service.integration;

import io.github.yusufakcay_dev.order_service.AbstractIntegrationTest;
import io.github.yusufakcay_dev.order_service.event.PaymentResultEvent;
import io.github.yusufakcay_dev.order_service.kafka.PaymentResultConsumer;
import io.github.yusufakcay_dev.order_service.entity.Order;
import io.github.yusufakcay_dev.order_service.entity.OrderItem;
import io.github.yusufakcay_dev.order_service.entity.OrderStatus;
import io.github.yusufakcay_dev.order_service.repository.OrderRepository;
import io.github.yusufakcay_dev.order_service.service.IdempotencyService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for idempotency and concurrent payment processing.
 * Uses Redis for idempotency checks to prevent duplicate order processing.
 */
@DisplayName("Idempotency Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentResultConsumer paymentResultConsumer;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IdempotencyService idempotencyService;

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

    private Order createTestOrder() {
        Order order = Order.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .paymentId("pay_test_" + UUID.randomUUID().toString().substring(0, 8))
                .customerEmail("test@example.com")
                .build();

        OrderItem item = OrderItem.builder()
                .sku("TEST-SKU")
                .quantity(1)
                .build();
        order.addItem(item);

        return orderRepository.save(order);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Should process first payment event and set idempotency key")
    void firstPaymentEvent_ShouldProcess() {
        // Given
        inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\":\"TEST-SKU\",\"availableQuantity\":99}")));

        Order order = createTestOrder();

        PaymentResultEvent event = PaymentResultEvent.builder()
                .orderId(order.getId())
                .paymentId(order.getPaymentId())
                .status("SUCCESS")
                .build();

        // When
        paymentResultConsumer.handlePaymentResult(event);

        // Then: Order should be PAID
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // And: Idempotency key should exist
        String key = idempotencyService.getOrderPaidKey(order.getId().toString());
        Boolean keyExists = redisTemplate.hasKey(key);
        assertThat(keyExists).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Should skip duplicate payment event due to idempotency")
    void duplicatePaymentEvent_ShouldBeSkipped() {
        // Given
        inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\":\"TEST-SKU\",\"availableQuantity\":99}")));

        Order order = createTestOrder();

        PaymentResultEvent event = PaymentResultEvent.builder()
                .orderId(order.getId())
                .paymentId(order.getPaymentId())
                .status("SUCCESS")
                .build();

        // When: Process the same event twice
        paymentResultConsumer.handlePaymentResult(event);
        paymentResultConsumer.handlePaymentResult(event); // Duplicate

        // Then: Inventory confirm should only be called ONCE
        inventoryMock.verify(1, postRequestedFor(urlPathMatching("/api/inventories/.*/confirm")));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Should handle concurrent payment events with idempotency protection")
    void concurrentPaymentEvents_OnlyOneProcessed() throws InterruptedException {
        // Given
        inventoryMock.stubFor(post(urlPathMatching("/api/inventories/.*/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\":\"TEST-SKU\",\"availableQuantity\":99}")));

        Order order = createTestOrder();

        PaymentResultEvent event = PaymentResultEvent.builder()
                .orderId(order.getId())
                .paymentId(order.getPaymentId())
                .status("SUCCESS")
                .build();

        // When: 5 threads try to process the same event concurrently
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger processedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start all at once
                    paymentResultConsumer.handlePaymentResult(event);
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for some threads due to concurrent access
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Wait a bit for async processing
        Thread.sleep(500);

        // Inventory confirm should be called at most once (idempotency)
        int confirmCalls = inventoryMock.findAll(postRequestedFor(urlPathMatching("/api/inventories/.*/confirm")))
                .size();
        assertThat(confirmCalls).isLessThanOrEqualTo(1);

        // Order should be in final state
        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finalOrder.getStatus()).isIn(OrderStatus.PAID, OrderStatus.PENDING);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("IdempotencyService should correctly identify first vs duplicate processing")
    void idempotencyService_FirstVsDuplicate() {
        // Given
        String testKey = "idempotency:test:" + UUID.randomUUID();

        // When: First call
        boolean firstResult = idempotencyService.isFirstProcessing(testKey);

        // Then: Should return true (first time)
        assertThat(firstResult).isTrue();

        // When: Second call with same key
        boolean secondResult = idempotencyService.isFirstProcessing(testKey);

        // Then: Should return false (duplicate)
        assertThat(secondResult).isFalse();

        // Cleanup
        idempotencyService.removeKey(testKey);
    }
}
