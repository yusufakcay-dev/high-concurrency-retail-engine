package io.github.yusufakcay_dev.user_service.integration;

import io.github.yusufakcay_dev.user_service.config.PostgresTestContainerConfig;
import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.AuthResponse;
import io.github.yusufakcay_dev.user_service.dto.UserResponse;
import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for user endpoints using PostgreSQL Testcontainers.
 * Tests user retrieval and data persistence verification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserIntegrationTest extends PostgresTestContainerConfig {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private void registerUser(String username, String password, String role) {
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);
        if (role != null) {
            request.setRole(role);
        }
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/auth/register", request,
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should get current user with valid header")
    void shouldGetCurrentUser() {
        registerUser("testuser", "password123", null);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", "testuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/user/me", HttpMethod.GET, entity, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo("testuser");
        assertThat(response.getBody().getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getBody().getId()).isNotNull();
    }

    @Test
    @DisplayName("Should return error for non-existent user")
    void shouldReturnErrorForNonExistentUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", "nonexistent");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/user/me", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Should hash password when persisting user")
    void shouldHashPassword() {
        registerUser("hashuser", "mypassword", null);

        User savedUser = userRepository.findByUsername("hashuser").orElse(null);

        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getPassword()).isNotEqualTo("mypassword");
        assertThat(savedUser.getPassword()).startsWith("$2a$"); // BCrypt prefix
    }

    @Test
    @DisplayName("Should handle multiple users with different roles")
    void shouldHandleMultipleUsersWithRoles() {
        registerUser("alice", "password123", "CUSTOMER");
        registerUser("bob", "password123", "ADMIN");

        HttpHeaders aliceHeaders = new HttpHeaders();
        aliceHeaders.set("X-User-Name", "alice");
        ResponseEntity<UserResponse> aliceResponse = restTemplate.exchange(
                "/user/me", HttpMethod.GET, new HttpEntity<>(aliceHeaders), UserResponse.class);

        HttpHeaders bobHeaders = new HttpHeaders();
        bobHeaders.set("X-User-Name", "bob");
        ResponseEntity<UserResponse> bobResponse = restTemplate.exchange(
                "/user/me", HttpMethod.GET, new HttpEntity<>(bobHeaders), UserResponse.class);

        assertThat(aliceResponse.getBody().getRole()).isEqualTo("CUSTOMER");
        assertThat(bobResponse.getBody().getRole()).isEqualTo("ADMIN");
        assertThat(userRepository.count()).isEqualTo(2);
    }
}
