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
                                                // Swagger/OpenAPI documentation - public
                                                .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**")
                                                .permitAll()
                                                .pathMatchers("/v3/api-docs/**", "/user-service/v3/api-docs/**",
                                                                "/product-service/v3/api-docs/**",
                                                                "/inventory-service/v3/api-docs/**",
                                                                "/order-service/v3/api-docs/**")
                                                .permitAll()

                                                // Authentication - public
                                                .pathMatchers("/auth/**", "/user-service/auth/**").permitAll()

                                                // Payment webhooks and redirects - public (for Stripe)
                                                .pathMatchers("/payments/webhook", "/payments/success",
                                                                "/payments/cancel")
                                                .permitAll()

                                                // Health endpoints - public (for monitoring/load balancers)
                                                .pathMatchers("/actuator/health", "/actuator/health/**",
                                                                "/actuator/info")
                                                .permitAll()

                                                // Products - public read, secured write (handled by
                                                // AuthenticationFilter)
                                                .pathMatchers("/products", "/products/**").permitAll()

                                                // All other routes are handled by AuthenticationFilter in routes config
                                                // Spring Security just prevents CSRF and basic auth prompts
                                                .anyExchange().permitAll())
                                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
                return http.build();
        }
}