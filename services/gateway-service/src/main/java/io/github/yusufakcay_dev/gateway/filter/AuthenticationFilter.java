package io.github.yusufakcay_dev.gateway.filter;

import io.github.yusufakcay_dev.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final RouteValidator validator;
    private final JwtUtil jwtUtil;

    public AuthenticationFilter(RouteValidator validator, JwtUtil jwtUtil) {
        super(Config.class);
        this.validator = validator;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (validator.isSecured.test(exchange.getRequest())) {
                // Check for Authorization header
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
                }

                String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return onError(exchange, "Invalid Authorization header format", HttpStatus.UNAUTHORIZED);
                }

                String token = authHeader.substring(7);

                try {
                    // Validate token
                    jwtUtil.validateToken(token);

                    // Extract username and role from token
                    String username = jwtUtil.extractUsername(token);
                    String role = jwtUtil.extractRole(token);

                    // Enforce ADMIN role for POST to products at gateway level
                    if (exchange.getRequest().getMethod() == HttpMethod.POST
                            && exchange.getRequest().getURI().getPath().startsWith("/products")) {
                        if (role == null || !role.equals("ADMIN")) {
                            log.warn("Forbidden: Non-admin user {} attempted POST to products", username);
                            return onError(exchange, "Access Denied: Admin only", HttpStatus.FORBIDDEN);
                        }
                    }

                    // Pass user info to downstream services
                    exchange = exchange.mutate()
                            .request(r -> r.header("X-User-Name", username)
                                    .header("X-User-Role", role))
                            .build();

                    log.debug("Authenticated user: {} with role: {}", username, role);
                } catch (Exception e) {
                    log.error("JWT validation failed: {}", e.getMessage());
                    return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
                }
            }
            return chain.filter(exchange);
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
    }
}