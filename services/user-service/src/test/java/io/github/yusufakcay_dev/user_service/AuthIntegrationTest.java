package io.github.yusufakcay_dev.user_service;

import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.AuthResponse;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterNewUser() {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
    }

    @Test
    void shouldNotRegisterDuplicateUsername() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldLoginWithValidCredentials() {
        // Register user first
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");
        restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        // Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", loginRequest, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
    }

    @Test
    void shouldNotLoginWithInvalidPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotLoginWithNonExistentUser() {
        AuthRequest request = new AuthRequest();
        request.setUsername("nonexistent");
        request.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithEmptyUsername() {
        AuthRequest request = new AuthRequest();
        request.setUsername("");
        request.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithEmptyPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotLoginWithEmptyUsername() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");
        loginRequest.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotLoginWithEmptyPassword() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithShortPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("short");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithNullUsername() {
        AuthRequest request = new AuthRequest();
        request.setUsername(null);
        request.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithNullPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword(null);
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotLoginWithNullUsername() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(null);
        loginRequest.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotLoginWithNullPassword() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword(null);
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithLongUsername() {
        String longUsername = "a".repeat(256); // Assuming max length is 255
        AuthRequest request = new AuthRequest();
        request.setUsername(longUsername);
        request.setPassword("password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldNotRegisterUserWithLongPassword() {
        String longPassword = "a".repeat(256); // Assuming max length is 255
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword(longPassword);
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

}