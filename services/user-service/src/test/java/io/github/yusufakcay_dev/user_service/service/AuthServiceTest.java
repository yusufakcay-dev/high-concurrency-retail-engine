package io.github.yusufakcay_dev.user_service.service;

import io.github.yusufakcay_dev.user_service.dto.AuthRequest;
import io.github.yusufakcay_dev.user_service.dto.LoginRequest;
import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService using Mockito.
 * Tests business logic without database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should register user with hashed password and default CUSTOMER role")
    void shouldRegisterUserWithHashedPasswordAndDefaultRole() {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedvalue");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt.token.here");

        String token = authService.register(request);

        assertThat(token).isEqualTo("jwt.token.here");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("newuser");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$hashedvalue");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.Role.CUSTOMER);
    }

    @Test
    @DisplayName("Should register user with ADMIN role when specified")
    void shouldRegisterUserWithAdminRole() {
        AuthRequest request = new AuthRequest();
        request.setUsername("admin");
        request.setPassword("password");
        request.setRole("ADMIN");

        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginWithValidCredentials() {
        User existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedPassword")
                .role(User.Role.CUSTOMER)
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("correctPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(existingUser)).thenReturn("login.jwt.token");

        String token = authService.login(request);

        assertThat(token).isEqualTo("login.jwt.token");
    }

    @Test
    @DisplayName("Should throw exception for wrong password")
    void shouldThrowExceptionForWrongPassword() {
        User existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedPassword")
                .role(User.Role.CUSTOMER)
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for non-existent user")
    void shouldThrowExceptionForNonExistentUser() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class);
    }
}
