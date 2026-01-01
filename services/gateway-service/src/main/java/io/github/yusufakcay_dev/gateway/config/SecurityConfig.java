package io.github.yusufakcay_dev.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

        @Bean
        public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .authorizeExchange(ex -> ex
                                                .pathMatchers(
                                                                "/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                                                "/v3/api-docs/**", "/user-service/v3/api-docs/**",
                                                                "/product-service/v3/api-docs/**",
                                                                "/inventory-service/v3/api-docs/**",
                                                                "/order-service/v3/api-docs/**",
                                                                "/auth/**", "/user-service/auth/**", "/user/me",
                                                                "/user-service/user/me",
                                                                "/products", "/products/**",
                                                                "/api/orders", "/api/orders/**",
                                                                "/actuator/health", "/actuator/health/**",
                                                                "/actuator/info", "/api/inventories/**",
                                                                "/inventories/**", "/api/inventory/")
                                                .permitAll()
                                                .anyExchange().authenticated())
                                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
                return http.build();
        }
}