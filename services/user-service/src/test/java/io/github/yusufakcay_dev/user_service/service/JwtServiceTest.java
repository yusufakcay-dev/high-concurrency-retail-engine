package io.github.yusufakcay_dev.user_service.service;

import io.github.yusufakcay_dev.user_service.config.PostgresTestContainerConfig;
import io.github.yusufakcay_dev.user_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JwtService with real Spring context.
 * Verifies JWT token generation and claims.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest extends PostgresTestContainerConfig {

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Should generate valid JWT token with correct structure")
    void shouldGenerateValidJwtToken() {
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedpassword")
                .role(User.Role.CUSTOMER)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(token).isNotNull().isNotEmpty();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("Should include correct claims in token payload")
    void shouldIncludeCorrectClaims() {
        User user = User.builder()
                .id(42L)
                .username("claimuser")
                .password("pass")
                .role(User.Role.ADMIN)
                .build();

        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payload).contains("\"sub\":\"claimuser\"");
        assertThat(payload).contains("\"role\":\"ADMIN\"");
        assertThat(payload).contains("\"userId\":42");
        assertThat(payload).contains("\"exp\":");
        assertThat(payload).contains("\"iat\":");
    }

    @Test
    @DisplayName("Should generate different tokens for different users")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        User user1 = User.builder().id(1L).username("user1").password("p").role(User.Role.CUSTOMER).build();
        User user2 = User.builder().id(2L).username("user2").password("p").role(User.Role.ADMIN).build();

        String token1 = jwtService.generateToken(user1);
        String token2 = jwtService.generateToken(user2);

        assertThat(token1).isNotEqualTo(token2);
    }
}
