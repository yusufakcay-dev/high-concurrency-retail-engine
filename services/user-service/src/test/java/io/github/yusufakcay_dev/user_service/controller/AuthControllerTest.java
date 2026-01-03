package io.github.yusufakcay_dev.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests using MockMvc.
 * Tests HTTP layer: request mapping, validation, response format.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /auth/register - should return JWT token")
    void shouldReturnTokenOnRegistration() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        when(authService.register(any(AuthRequest.class))).thenReturn("generated.jwt.token");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("generated.jwt.token"));
    }

    @Test
    @DisplayName("POST /auth/register - should reject invalid data")
    void shouldRejectInvalidRegistration() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("ab"); // Too short
        request.setPassword("short"); // Too short

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login - should return JWT token")
    void shouldReturnTokenOnLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("existinguser");
        request.setPassword("correctpassword");

        when(authService.login(any(LoginRequest.class))).thenReturn("login.jwt.token");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login.jwt.token"));
    }

    @Test
    @DisplayName("POST /auth/login - should reject empty credentials")
    void shouldRejectEmptyLoginCredentials() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
