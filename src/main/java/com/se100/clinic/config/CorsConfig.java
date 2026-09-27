package com.se100.clinic.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS: only origins listed in {@code app.cors.allowed-origins} (env var {@code
 * CORS_ALLOWED_ORIGINS}). Never {@code "*"} — combined with credentials it is a security hole, and
 * browsers reject that combination anyway.
 *
 * <p>Equivalent to NestJS {@code app.enableCors({ origin: [...] })} or ASP.NET Core {@code
 * services.AddCors(...)}. Spring Security picks this bean up via {@code
 * .cors(Customizer.withDefaults())} in {@link SecurityConfig}.
 */
@Configuration
public class CorsConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
