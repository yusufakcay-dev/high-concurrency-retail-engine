package io.github.yusufakcay_dev.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

        @Value("${gateway.host:localhost}")
        private String gatewayHost;

        @Value("${gateway.port:8080}")
        private String gatewayPort;

        @Bean
        public OpenAPI customOpenAPI() {
                String serverUrl = gatewayHost.startsWith("http")
                                ? gatewayHost + ":" + gatewayPort
                                : "http://" + gatewayHost + ":" + gatewayPort;

                return new OpenAPI()
                                .servers(List.of(
                                                new Server()
                                                                .url(serverUrl)
                                                                .description("API Gateway")))
                                .info(new Info()
                                                .title("Retail Engine API Gateway")
                                                .version("1.0")
                                                .description("High Concurrency Retail Engine - API Gateway"))
                                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                                .components(new Components()
                                                .addSecuritySchemes("bearerAuth",
                                                                new SecurityScheme()
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")));
        }
}