package com.se100.clinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security SKELETON — no real login/JWT yet (see docs/setup/DECISIONS.md #5; it will be
 * built with the {@code identity} module).
 *
 * <p>Plays the role of NestJS Guards ({@code AuthGuard}/{@code RolesGuard}) or ASP.NET Core's
 * {@code [Authorize]} + auth middleware, except that Spring declares route rules centrally in a
 * {@code SecurityFilterChain} instead of on each controller.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** Health/info must stay reachable without login (Docker healthcheck, load balancer). */
  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info"
  };

  /**
   * {@code dev} and {@code test}: every endpoint is open, so the team can call the API from
   * curl/Postman/Swagger UI and the {@code doctor} tests need no fake auth. {@code application.yml}
   * makes {@code dev} the default profile.
   *
   * <p>TODO(identity): replace with OAuth2 Resource Server + JWT, authorizing by {@link
   * com.se100.clinic.shared.Role}.
   */
  @Bean
  @Profile({"dev", "test"})
  public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
    // CSRF protects cookie-based browser sessions; this is a stateless JSON API.
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /**
   * {@code prod}: fail closed — everything except health/info requires authentication. With no
   * authentication mechanism configured yet, those requests get 401. TEMPORARY until {@code
   * identity} exists; do not deploy a real prod with this.
   */
  @Bean
  @Profile("prod")
  public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll().anyRequest().authenticated());
    return http.build();
  }
}
