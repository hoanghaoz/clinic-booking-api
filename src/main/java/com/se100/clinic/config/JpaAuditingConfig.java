package com.se100.clinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@link com.se100.clinic.shared.BaseAuditEntity} gets {@code
 * createdAt}/{@code updatedAt} filled automatically. Kept separate from the
 * {@code @SpringBootApplication} class so that class stays small as the app grows.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
