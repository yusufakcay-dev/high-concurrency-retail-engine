package io.github.yusufakcay_dev.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "dGhpc2lzYXZlcnlsb25nc2VjcmV0a2V5Zm9ydGVzdGluZ2p3dHRva2VuczEyMzQ1Njc4OTA=";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String generateToken(String username, String role, long expirationMs) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldValidateValidToken() {
        String token = generateToken("testuser", "USER", 3600000);
        jwtUtil.validateToken(token);
        // No exception means success
    }

    @Test
    void shouldThrowExceptionForExpiredToken() {
        // Token expired 2 minutes ago (exceeds 60s clock skew allowance)
        String token = generateToken("testuser", "USER", -120000);

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void shouldThrowExceptionForInvalidSignature() {
        Key wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                "d3JvbmdzZWNyZXRrZXlmb3J0ZXN0aW5nd3JvbmdzZWNyZXRrZXkxMjM0NTY3ODkwMTI="));
        String token = Jwts.builder()
                .setSubject("testuser")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void shouldExtractUsername() {
        String token = generateToken("admin@example.com", "ADMIN", 3600000);

        String username = jwtUtil.extractUsername(token);

        assertThat(username).isEqualTo("admin@example.com");
    }

    @Test
    void shouldExtractRole() {
        String token = generateToken("testuser", "ADMIN", 3600000);

        String role = jwtUtil.extractRole(token);

        assertThat(role).isEqualTo("ADMIN");
    }
}
