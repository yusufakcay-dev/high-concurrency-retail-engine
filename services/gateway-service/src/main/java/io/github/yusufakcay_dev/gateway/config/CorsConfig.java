// package io.github.yusufakcay_dev.gateway.config;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.reactive.CorsWebFilter;
// import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

// import java.util.Arrays;
// import java.util.List;

// @Configuration
// public class CorsConfig {

// @Bean
// public CorsWebFilter corsWebFilter() {
// CorsConfiguration config = new CorsConfiguration();
// config.setAllowCredentials(true);
// config.setAllowedOrigins(List.of("http://localhost:3000")); // Your frontend
// URL
// config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE",
// "OPTIONS"));
// config.setAllowedHeaders(List.of("*"));

// UrlBasedCorsConfigurationSource source = new
// UrlBasedCorsConfigurationSource();
// source.registerCorsConfiguration("/**", config);

// return new CorsWebFilter(source);
// }
// }