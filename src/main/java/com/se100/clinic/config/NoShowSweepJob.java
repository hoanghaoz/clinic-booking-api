package com.se100.clinic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SKELETON — no logic yet.
 *
 * <p>Spec (docs/de-tai.md section E): at end of day, mark past appointments the patient did not
 * attend as no-show; 3+ no-shows in 6 months records a violation code and blocks online booking for
 * 3 months.
 *
 * <p>TODO(booking, violation): find confirmed appointments past their time and publish an event
 * that {@code violation} handles ({@code ApplicationEventPublisher} +
 * {@code @TransactionalEventListener}) rather than calling its service inside the same transaction,
 * so the two modules stay decoupled.
 *
 * <p>Runs daily at 23:00 in {@code app.timezone}.
 */
@Component
class NoShowSweepJob {

  private static final Logger log = LoggerFactory.getLogger(NoShowSweepJob.class);

  @Scheduled(cron = "0 0 23 * * *", zone = "${app.timezone}")
  void sweepNoShowAppointments() {
    log.info("[skeleton] NoShowSweepJob ran — no logic yet.");
  }
}
