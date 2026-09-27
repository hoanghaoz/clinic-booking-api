package com.se100.clinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs — like {@code ScheduleModule.forRoot()} in @nestjs/schedule or an
 * ASP.NET Core {@code BackgroundService}.
 *
 * <p>The two job skeletons ({@link CardExpiryReminderJob}, {@link NoShowSweepJob}) live here only
 * because their modules ({@code patient}, {@code violation}) are still stubs. Move each job into
 * its module when that module is built, and inject the real service through the constructor.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
