package com.se100.clinic.notification.dtos;

import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationType;
import java.util.Set;

/**
 * Channel-independent description of one notification to a patient.
 *
 * <p>The notification type decides what is said (title/content are built once per type); the
 * channel strategies only decide how it is delivered. {@code dedupKey} identifies the business
 * event (for example patient + card expiry date); each channel prefixes it, so the same event can
 * be delivered once per channel and a redelivered message never creates a duplicate.
 *
 * <p>{@code channels} says where to send it. An empty set means "every available channel", so use
 * {@link #forAllChannels} unless the notification must go out on specific channels only.
 */
public record NotificationRequest(
    NotificationType type,
    Long patientId,
    String title,
    String content,
    String dedupKey,
    Set<NotificationChannel> channels) {

  public NotificationRequest {
    // Treat null as "no preference" and make the set immutable.
    channels = channels == null ? Set.of() : Set.copyOf(channels);
  }

  /** A notification that goes out on every available channel. */
  public static NotificationRequest forAllChannels(
      NotificationType type, Long patientId, String title, String content, String dedupKey) {
    return new NotificationRequest(type, patientId, title, content, dedupKey, Set.of());
  }

  public boolean isForAllChannels() {
    return channels.isEmpty();
  }
}
