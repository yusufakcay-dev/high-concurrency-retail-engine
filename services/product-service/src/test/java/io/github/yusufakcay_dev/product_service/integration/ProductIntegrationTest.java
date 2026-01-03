package io.github.yusufakcay_dev.product_service.integration;

import io.github.yusufakcay_dev.product_service.dto.ProductRequest;
import io.github.yusufakcay_dev.product_service.dto.ProductStockStatusEvent;
import io.github.yusufakcay_dev.product_service.entity.Product;
import io.github.yusufakcay_dev.product_service.repository.ProductRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.kafka.bootstrap-servers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.kafka.bootstrap-servers}"
})
@ComponentScan(basePackages = "io.github.yusufakcay_dev.product_service", excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = io.github.yusufakcay_dev.product_service.config.TestKafkaConfig.class))
@Testcontainers
class ProductIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("productdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("deprecation")
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private KafkaMessageListenerContainer<String, Object> container;
    private BlockingQueue<ConsumerRecord<String, Object>> records;
    private KafkaTemplate<String, ProductStockStatusEvent> testKafkaTemplate;

    private KafkaTemplate<String, ProductStockStatusEvent> createTestKafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        DefaultKafkaProducerFactory<String, ProductStockStatusEvent> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProps);
        return new KafkaTemplate<>(producerFactory);
    }

    private <T> HttpEntity<T> createAdminRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", "admin");
        headers.set("X-User-Role", "ADMIN");
        return new HttpEntity<>(body, headers);
    }

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        if (cacheManager.getCache("products") != null) {
            cacheManager.getCache("products").clear();
        }

        // Initialize test Kafka template for sending events
        testKafkaTemplate = createTestKafkaTemplate();

        // Set up Kafka consumer to verify events
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                "io.github.yusufakcay_dev.product_service.dto.ProductCreatedEvent");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        DefaultKafkaConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProps = new ContainerProperties("product-created-topic");

        records = new LinkedBlockingQueue<>();
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.setupMessageListener((MessageListener<String, Object>) records::add);
        container.start();
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
        productRepository.deleteAll();
    }

    @Test
    void testCreateProduct_SavesInDatabase() {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setName("Integration Test Product");
        request.setDescription("Created via integration test");
        request.setPrice(BigDecimal.valueOf(199.99));
        request.setSku("INT-TEST-001");
        request.setInitialStock(100);

        // Act
        ResponseEntity<Product> response = restTemplate.postForEntity(
                "/products",
                createAdminRequest(request),
                Product.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Integration Test Product");
        assertThat(response.getBody().getSku()).isEqualTo("INT-TEST-001");

        // Verify database persistence
        Product savedProduct = productRepository.findBySku("INT-TEST-001").orElse(null);
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getName()).isEqualTo("Integration Test Product");
    }

    @Test
    void testCreateProduct_PublishesKafkaEvent() throws InterruptedException {
        // Arrange
        ProductRequest request = new ProductRequest();
        request.setName("Kafka Test Product");
        request.setDescription("Testing Kafka event");
        request.setPrice(BigDecimal.valueOf(299.99));
        request.setSku("KAFKA-TEST-001");
        request.setInitialStock(50);

        // Act
        ResponseEntity<Product> response = restTemplate.postForEntity(
                "/products",
                createAdminRequest(request),
                Product.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify Kafka event was published
        ConsumerRecord<String, Object> record = records.poll(15, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo("product-created-topic");
    }

    @Test
    void testStockStatusUpdate_UpdatesProductAndEvictsCache() {
        // Arrange - create a product first
        Product product = Product.builder()
                .name("Cache Test Product")
                .description("Testing cache eviction")
                .price(BigDecimal.valueOf(99.99))
                .sku("CACHE-TEST-001")
                .active(true)
                .inStock(true)
                .build();
        Product savedProduct = productRepository.save(product);

        // Access product to cache it
        ResponseEntity<Product> firstResponse = restTemplate.getForEntity(
                "/products/" + savedProduct.getId(),
                Product.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody().getInStock()).isTrue();

        // Act - send stock status update via Kafka
        ProductStockStatusEvent event = ProductStockStatusEvent.builder()
                .sku("CACHE-TEST-001")
                .inStock(false)
                .build();
        testKafkaTemplate.send("product-stock-status-topic", event);

        // Wait for consumer to process the message
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
                    assertThat(updatedProduct.getInStock()).isFalse();
                });

        // Assert - verify cache was evicted and new data is returned
        ResponseEntity<Product> secondResponse = restTemplate.getForEntity(
                "/products/" + savedProduct.getId(),
                Product.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody().getInStock()).isFalse();
    }

    @Test
    void testCreateProduct_DuplicateSku_ReturnsBadRequest() {
        // Arrange - create first product
        ProductRequest request1 = new ProductRequest();
        request1.setName("Original Product");
        request1.setDescription("First product with SKU");
        request1.setPrice(BigDecimal.valueOf(100.00));
        request1.setSku("DUPLICATE-SKU");
        request1.setInitialStock(10);

        restTemplate.postForEntity("/products", createAdminRequest(request1), Product.class);

        // Act - try to create duplicate
        ProductRequest request2 = new ProductRequest();
        request2.setName("Duplicate Product");
        request2.setDescription("Second product with same SKU");
        request2.setPrice(BigDecimal.valueOf(150.00));
        request2.setSku("DUPLICATE-SKU");
        request2.setInitialStock(20);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/products",
                createAdminRequest(request2),
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(productRepository.count()).isEqualTo(1);
    }
}
