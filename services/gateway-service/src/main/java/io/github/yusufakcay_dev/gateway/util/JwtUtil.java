package io.github.yusufakcay_dev.gateway.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;

@Slf4j
@Component
public class JwtUtil {

    @Value("${application.security.jwt.secret}")
    private String secret;

    private static final long CLOCK_SKEW_SEC = 60;
    private volatile Key cachedSigningKey;
    private volatile JwtParser cachedParser;

    public void validateToken(String token) {
        try {
            getParser().parseClaimsJws(token);
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            throw new RuntimeException("Invalid token");
        }
    }

    private Key getSigningKey() {
        Key local = cachedSigningKey;
        if (local == null) {
            synchronized (this) {
                local = cachedSigningKey;
                if (local == null) {
                    byte[] keyBytes = Decoders.BASE64.decode(secret);
                    local = Keys.hmacShaKeyFor(keyBytes);
                    cachedSigningKey = local;
                }
            }
        }
        return local;
    }

    private JwtParser getParser() {
        JwtParser local = cachedParser;
        if (local == null) {
            synchronized (this) {
                local = cachedParser;
                if (local == null) {
                    local = Jwts.parserBuilder()
                            .setSigningKey(getSigningKey())
                            .setAllowedClockSkewSeconds(CLOCK_SKEW_SEC)
                            .build();
                    cachedParser = local;
                }
            }
        }
        return local;
    }

    public String extractUsername(String token) {
        return getParser()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String extractRole(String token) {
        return getParser()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }
}