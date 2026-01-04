package io.github.yusufakcay_dev.product_service.config;

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

        @Value("${gateway.host:localhost}")
        private String gatewayHost;

        @Value("${gateway.port:8080}")
        private String gatewayPort;

        @Bean
        public OpenAPI customOpenAPI() {
                final String securitySchemeName = "bearerAuth";
                String serverUrl = gatewayHost.startsWith("http")
                                ? gatewayHost + ":" + gatewayPort
                                : "http://" + gatewayHost + ":" + gatewayPort;

                return new OpenAPI()
                                .servers(List.of(
                                                new Server()
                                                                .url(serverUrl)
                                                                .description("API Gateway")))
                                .info(new Info()
                                                .title("Product Service API")
                                                .version("1.0")
                                                .description("Product management and catalog services"))
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