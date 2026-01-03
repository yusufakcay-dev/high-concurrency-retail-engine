package io.github.yusufakcay_dev.payment_service.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers configuration for payment-service tests.
 * Containers are started once and reused across all test classes.
 */
@SuppressWarnings({ "resource", "deprecation" })
public abstract class TestContainersConfig {

    protected static final PostgreSQLContainer<?> postgres;
    protected static final KafkaContainer kafka;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("payment_test_db")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        postgres.start();

        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withReuse(true);
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Stripe test configuration
        registry.add("stripe.api-key", () -> "sk_test_fake_key");
        registry.add("stripe.webhook-secret", () -> "whsec_test_fake_secret");
        registry.add("stripe.success-url", () -> "http://localhost:3000/success");
        registry.add("stripe.cancel-url", () -> "http://localhost:3000/cancel");
    }
}
