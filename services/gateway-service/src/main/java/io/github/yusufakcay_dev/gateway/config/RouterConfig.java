package io.github.yusufakcay_dev.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class RouterConfig {

    /**
     * Redirect root path to Swagger UI for easy API documentation access
     */
    @Bean
    public RouterFunction<ServerResponse> rootRedirect() {
        return RouterFunctions.route(
                GET("/"),
                req -> ServerResponse
                        .status(HttpStatus.TEMPORARY_REDIRECT)
                        .location(URI.create("/swagger-ui.html"))
                        .build());
    }
}
