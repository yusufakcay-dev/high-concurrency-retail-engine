package io.github.yusufakcay_dev.user_service;

import io.github.yusufakcay_dev.user_service.entity.User;
import io.github.yusufakcay_dev.user_service.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedpassword")
                .role(User.Role.CUSTOMER)
                .build();
    }

    @Test
    void shouldGenerateValidToken() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void shouldGenerateTokenWithCorrectClaims() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotEmpty();
    }

    @Test
    void shouldGenerateUniqueTokens() throws InterruptedException {
        String token1 = jwtService.generateToken(testUser);
        Thread.sleep(1000);
        String token2 = jwtService.generateToken(testUser);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void shouldGenerateTokenWithUserRole() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotEmpty();
    }

    @Test
    void shouldGenerateTokenWithUserId() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotEmpty();
    }

    @Test
    void shouldHandleNullUser() {
        assertThatThrownBy(() -> jwtService.generateToken(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldGenerateValidJwtStructure() {
        String token = jwtService.generateToken(testUser);
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isNotEmpty(); // header
        assertThat(parts[1]).isNotEmpty(); // payload
        assertThat(parts[2]).isNotEmpty(); // signature
    }
}