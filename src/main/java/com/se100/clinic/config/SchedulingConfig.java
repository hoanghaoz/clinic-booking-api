package com.se100.clinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs — like {@code ScheduleModule.forRoot()} in @nestjs/schedule or an
 * ASP.NET Core {@code BackgroundService}.
 *
 * <p>Each job lives in the module that owns its data, for example the card expiry reminder job in
 * the {@code patient} module. The {@link NoShowSweepJob} skeleton stays here only because its
 * module ({@code violation}) is still a stub. Move it into that module when it is built, and inject
 * the real service through the constructor.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
