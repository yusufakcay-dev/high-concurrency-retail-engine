package io.github.yusufakcay_dev.order_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests using TestContainers.
 * Provides PostgreSQL, Redis, and Kafka containers.
 * 
 * Each test class that needs WireMock should configure its own servers
 * using @DynamicPropertySource in the subclass.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

        @SuppressWarnings("resource")
        @Container
        protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
                        DockerImageName.parse("postgres:15-alpine"))
                        .withDatabaseName("test_db")
                        .withUsername("test")
                        .withPassword("test");

        @SuppressWarnings("resource")
        @Container
        protected static final GenericContainer<?> redisContainer = new GenericContainer<>(
                        DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);

        @SuppressWarnings("deprecation")
        @Container
        protected static final KafkaContainer kafkaContainer = new KafkaContainer(
                        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                // PostgreSQL
                registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
                registry.add("spring.datasource.username", postgresContainer::getUsername);
                registry.add("spring.datasource.password", postgresContainer::getPassword);

                // Redis
                registry.add("spring.data.redis.host", redisContainer::getHost);
                registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379).toString());
                registry.add("spring.redis.host", redisContainer::getHost);
                registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379).toString());

                // Kafka
                registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        }
}
