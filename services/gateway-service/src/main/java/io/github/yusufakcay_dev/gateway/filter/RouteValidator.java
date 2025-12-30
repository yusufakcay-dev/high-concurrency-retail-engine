package io.github.yusufakcay_dev.gateway.filter;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.HttpMethod;
import java.util.function.Predicate;

@Component
public class RouteValidator {

        private static final List<String> openApiEndpoints = List.of(
                        "/auth/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/user-service/v3/api-docs/**",
                        "/product-service/v3/api-docs/**");

        private final AntPathMatcher matcher = new AntPathMatcher();

        public Predicate<ServerHttpRequest> isSecured = request -> {
                // Allow public GET requests to products
                if (request.getMethod() == HttpMethod.GET && request.getURI().getPath().startsWith("/products")) {
                        return false;
                }

                return openApiEndpoints
                                .stream()
                                .noneMatch(uri -> matcher.match(uri, request.getURI().getPath()));
        };
}