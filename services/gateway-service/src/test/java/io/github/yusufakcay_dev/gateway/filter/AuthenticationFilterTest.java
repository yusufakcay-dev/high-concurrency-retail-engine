package io.github.yusufakcay_dev.gateway.filter;

import io.github.yusufakcay_dev.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain filterChain;

    private AuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        authenticationFilter = new AuthenticationFilter(jwtUtil);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldRejectRequestWithoutAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/inventories/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    @Test
    void shouldRejectRequestWithInvalidAuthHeaderFormat() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/inventories/123")
                .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowRequestWithValidToken() {
        String validToken = "valid.jwt.token";

        when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
        when(jwtUtil.extractRole(validToken)).thenReturn("USER");
        doNothing().when(jwtUtil).validateToken(validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(any());
    }

    @Test
    void shouldForbidNonAdminPostToProducts() {
        String validToken = "valid.jwt.token";

        when(jwtUtil.extractUsername(validToken)).thenReturn("regularuser");
        when(jwtUtil.extractRole(validToken)).thenReturn("USER");
        doNothing().when(jwtUtil).validateToken(validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(filterChain, never()).filter(any());
    }

    @Test
    void shouldAllowAdminPostToProducts() {
        String validToken = "valid.jwt.token";

        when(jwtUtil.extractUsername(validToken)).thenReturn("admin");
        when(jwtUtil.extractRole(validToken)).thenReturn("ADMIN");
        doNothing().when(jwtUtil).validateToken(validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(any());
    }

    @Test
    void shouldForbidNonAdminAccessToInventories() {
        String validToken = "valid.jwt.token";

        when(jwtUtil.extractUsername(validToken)).thenReturn("regularuser");
        when(jwtUtil.extractRole(validToken)).thenReturn("USER");
        doNothing().when(jwtUtil).validateToken(validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/inventories/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = authenticationFilter.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
