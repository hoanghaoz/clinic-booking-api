package com.se100.clinic.config;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless Bearer authentication; current account state and roles are checked on each request. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout"
  };

  /** Read-only lookups that guests may call; every write on these paths stays admin-only. */
  private static final String[] PUBLIC_READ_ENDPOINTS = {
    "/api/v1/doctors", "/api/v1/doctors/*", "/api/v1/specialties", "/api/v1/specialties/*"
  };

  private static void securityError(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    if (status == 401) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\"}");
  }

  @Bean
  @Profile({"dev", "test"})
  public SecurityFilterChain devFilterChain(
      HttpSecurity http, ObjectProvider<JwtTokenService> tokens, AuthenticatedUserService users)
      throws Exception {
    return configure(http, tokens.getIfAvailable(), users, false);
  }

  @Bean
  @Profile("prod")
  public SecurityFilterChain prodFilterChain(
      HttpSecurity http, JwtTokenService tokens, AuthenticatedUserService users) throws Exception {
    return configure(http, tokens, users, true);
  }

  private SecurityFilterChain configure(
      HttpSecurity http, JwtTokenService tokens, AuthenticatedUserService users, boolean production)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> securityError(response, 401, "AUTH_REQUIRED"))
                    .accessDeniedHandler(
                        (request, response, error) -> securityError(response, 403, "FORBIDDEN")))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
              auth.requestMatchers(HttpMethod.GET, PUBLIC_READ_ENDPOINTS).permitAll();
              if (tokens != null || production) {
                auth.anyRequest().authenticated();
              } else {
                auth.anyRequest().permitAll();
              }
            });
    if (tokens != null) {
      http.oauth2ResourceServer(
          oauth ->
              oauth
                  .authenticationEntryPoint(
                      (request, response, error) -> securityError(response, 401, "AUTH_REQUIRED"))
                  .accessDeniedHandler(
                      (request, response, error) -> securityError(response, 403, "FORBIDDEN"))
                  .jwt(
                      jwt ->
                          jwt.decoder(tokens.decoder())
                              .jwtAuthenticationConverter(
                                  token -> {
                                    var identity =
                                        users
                                            .identityForSubject(token.getSubject())
                                            .orElseThrow(
                                                () ->
                                                    new OAuth2AuthenticationException(
                                                        "invalid_token"));
                                    return new JwtAuthenticationToken(
                                        token,
                                        List.of(
                                            new SimpleGrantedAuthority("ROLE_" + identity.role())),
                                        identity.username());
                                  })));
    }
    return http.build();
  }
}
