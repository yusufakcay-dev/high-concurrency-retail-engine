package io.github.yusufakcay_dev.user_service.integration;

import io.github.yusufakcay_dev.user_service.config.PostgresTestContainerConfig;
import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.AuthResponse;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication endpoints using PostgreSQL
 * Testcontainers.
 * Tests the full request/response cycle including database persistence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest extends PostgresTestContainerConfig {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should register new user and persist to PostgreSQL")
    void shouldRegisterNewUser() {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
        assertThat(response.getBody().getToken().split("\\.")).hasSize(3); // Valid JWT structure
        assertThat(userRepository.findByUsername("newuser")).isPresent();
    }

    @Test
    @DisplayName("Should register user with specific role")
    void shouldRegisterUserWithRole() {
        AuthRequest request = new AuthRequest();
        request.setUsername("adminuser");
        request.setPassword("password123");
        request.setRole("ADMIN");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var savedUser = userRepository.findByUsername("adminuser");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getRole().name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should reject duplicate username")
    void shouldRejectDuplicateUsername() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should reject invalid registration data")
    void shouldRejectInvalidRegistration() {
        AuthRequest request = new AuthRequest();
        request.setUsername("ab"); // Too short
        request.setPassword("short"); // Too short

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should login with valid credentials")
    void shouldLoginWithValidCredentials() {
        // Register user first
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername("loginuser");
        registerRequest.setPassword("password123");
        restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        // Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("loginuser");
        loginRequest.setPassword("password123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", loginRequest, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void shouldRejectLoginWithWrongPassword() {
        // Register user first
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername("loginuser");
        registerRequest.setPassword("password123");
        restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        // Login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("loginuser");
        loginRequest.setPassword("wrongpassword");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Should reject login for non-existent user")
    void shouldRejectLoginForNonExistentUser() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("nonexistent");
        loginRequest.setPassword("password123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
