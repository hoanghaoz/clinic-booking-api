package com.se100.clinic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SKELETON — no logic yet.
 *
 * <p>Spec (docs/de-tai.md section C): one month before a patient card expires, remind the patient
 * by email / in-app notification.
 *
 * <p>TODO(patient, notification): inject the patient card service once it exists, find cards
 * expiring within 30 days, and send reminders through the {@code notification} module.
 *
 * <p>Runs daily at 07:00 in {@code app.timezone} (Asia/Ho_Chi_Minh).
 */
@Component
class CardExpiryReminderJob {

  private static final Logger log = LoggerFactory.getLogger(CardExpiryReminderJob.class);

  @Scheduled(cron = "0 0 7 * * *", zone = "${app.timezone}")
  void remindExpiringCards() {
    log.info("[skeleton] CardExpiryReminderJob ran — no logic yet.");
  }
}
