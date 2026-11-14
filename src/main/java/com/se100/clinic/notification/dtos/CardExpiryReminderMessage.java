package com.se100.clinic.notification.dtos;

import java.time.Instant;

public record CardExpiryReminderMessage(
  Long patientId,
  Instant expiresAt
) {
  public static CardExpiryReminderMessage of(Long patientId, Instant expiresAt) {
    return new CardExpiryReminderMessage(patientId, expiresAt);
  }
}
