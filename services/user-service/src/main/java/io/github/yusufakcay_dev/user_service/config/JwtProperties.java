package io.github.yusufakcay_dev.user_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "application.security.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT Secret is required in application.yml")
    private String secret;
}