package io.github.yusufakcay_dev.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RouteValidatorTest {

    private RouteValidator routeValidator;

    @BeforeEach
    void setUp() {
        routeValidator = new RouteValidator();
    }

    @Test
    void shouldAllowAuthEndpointsWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/auth/login")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isFalse();
    }

    @Test
    void shouldAllowSwaggerEndpointsWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/swagger-ui/index.html")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isFalse();
    }

    @Test
    void shouldAllowPublicGetProductsWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/products")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isFalse();
    }

    @Test
    void shouldRequireAuthForPostProducts() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/products")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isTrue();
    }

    @Test
    void shouldRequireAuthForInventoryEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/inventories/123")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isTrue();
    }

    @Test
    void shouldAllowPaymentWebhookWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/payments/webhook")
                .build();

        boolean isSecured = routeValidator.isSecured.test(request);

        assertThat(isSecured).isFalse();
    }
}
