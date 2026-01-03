package io.github.yusufakcay_dev.user_service.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base configuration for tests using PostgreSQL Testcontainer.
 * Uses singleton pattern - container is started once and reused by all tests.
 * This significantly improves test execution time.
 */
public abstract class PostgresTestContainerConfig {

    // Singleton container - started once, reused by all tests
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("user_test_db")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Enable container reuse across test runs
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
