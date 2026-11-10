package com.se100.clinic.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single injectable {@link Clock} in the application time zone ({@code app.timezone}), so time
 * based rules (e.g. patient card expiry) can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

  @Bean
  Clock clock(@Value("${app.timezone}") String timezone) {
    return Clock.system(ZoneId.of(timezone));
  }
}
