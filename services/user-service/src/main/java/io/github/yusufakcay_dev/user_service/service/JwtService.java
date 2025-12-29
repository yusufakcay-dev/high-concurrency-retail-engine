package io.github.yusufakcay_dev.user_service.service;

import io.github.yusufakcay_dev.user_service.config.JwtProperties;
import io.github.yusufakcay_dev.user_service.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private volatile Key cachedSigningKey;

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24 Hours
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSigningKey() {
        Key local = cachedSigningKey;
        if (local == null) {
            synchronized (this) {
                local = cachedSigningKey;
                if (local == null) {
                    byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
                    local = Keys.hmacShaKeyFor(keyBytes);
                    cachedSigningKey = local;
                }
            }
        }
        return local;
    }
}