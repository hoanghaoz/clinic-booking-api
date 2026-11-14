package com.se100.clinic.notification.dtos;

import java.time.Instant;

public record CardExpiryReminderMessage(
  Long patientId,
  String cardNumber,
  Instant expiresAt
) {
  public static CardExpiryReminderMessage of(Long patientId, String cardNumber, Instant expiresAt) {
    return new CardExpiryReminderMessage(patientId, cardNumber, expiresAt);
  }
}
