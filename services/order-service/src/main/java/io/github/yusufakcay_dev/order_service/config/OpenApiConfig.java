package io.github.yusufakcay_dev.order_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${GATEWAY_PUBLIC_URL:http://localhost:8080}")
        private String gatewayUrl;

        @Bean
        public OpenAPI customOpenAPI() {
                final String securitySchemeName = "bearerAuth";

                return new OpenAPI()
                                .servers(List.of(
                                                new Server()
                                                                .url(gatewayUrl)
                                                                .description("API Gateway")))
                                .info(new Info()
                                                .title("Order Service API")
                                                .version("1.0")
                                                .description("Order processing and management"))
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name(securitySchemeName)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")));
        }
}