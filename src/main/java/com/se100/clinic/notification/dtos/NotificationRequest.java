package com.se100.clinic.notification.dtos;

import com.se100.clinic.notification.enums.NotificationType;

/**
 * Channel-independent description of one notification to a patient.
 *
 * <p>The notification type decides what is said (title/content are built once per type); the
 * channel strategies only decide how it is delivered. {@code dedupKey} identifies the business
 * event (for example patient + card expiry date); each channel prefixes it, so the same event can
 * be delivered once per channel and a redelivered message never creates a duplicate.
 */
public record NotificationRequest(
    NotificationType type, Long patientId, String title, String content, String dedupKey) {}
