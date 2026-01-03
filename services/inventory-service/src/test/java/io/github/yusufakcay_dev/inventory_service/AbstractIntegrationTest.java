package io.github.yusufakcay_dev.inventory_service;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests using TestContainers.
 * Provides PostgreSQL, Redis, and Kafka containers for realistic testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // PostgreSQL Container
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // Redis Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    // Kafka Container
    @SuppressWarnings({ "deprecation", "resource" })
    protected static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withReuse(true);

    @BeforeAll
    static void startContainers() {
        // Start all containers
        postgresContainer.start();
        redisContainer.start();
        kafkaContainer.start();
    }

    /**
     * Configure Spring properties dynamically from TestContainers
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        // Redis properties
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }
}
