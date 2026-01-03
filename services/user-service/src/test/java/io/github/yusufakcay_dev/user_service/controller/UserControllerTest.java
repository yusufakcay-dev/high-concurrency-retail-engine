package io.github.yusufakcay_dev.user_service.controller;

import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for user endpoints using MockMvc.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /user/me - should return user info with valid header")
    void shouldReturnUserInfo() throws Exception {
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedpass")
                .role(User.Role.CUSTOMER)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/user/me")
                .header("X-User-Name", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /user/me - should return 500 when user not found")
    void shouldReturn500WhenUserNotFound() throws Exception {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/user/me")
                .header("X-User-Name", "nonexistent"))
                .andExpect(status().isInternalServerError());
    }
}
