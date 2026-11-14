package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.notification.enums.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Checks that claiming a notification for sending is atomic, against a real PostgreSQL. */
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final List<NotificationStatus> CLAIMABLE =
      List.of(NotificationStatus.PENDING, NotificationStatus.FAILED);
  private static final Duration LOCK = Duration.ofMinutes(5);

  @Autowired private NotificationRepository notificationRepository;

  private String saveNotification(NotificationStatus status, Instant lockedUntil) {
    String key = "EMAIL:CARD_EXPIRING:" + UUID.randomUUID();
    Instant now = Instant.now();
    Notification notification = new Notification();
    notification.setChannel(NotificationChannel.EMAIL);
    notification.setType(NotificationType.CARD_EXPIRING);
    notification.setTitle("title");
    notification.setContent("content");
    notification.setDeduplicationKey(key);
    notification.setStatus(status);
    notification.setLockedUntil(lockedUntil);
    notification.setScheduledAt(now);
    notification.setNextAttemptAt(now);
    notificationRepository.save(notification);
    return key;
  }

  private int claim(String key, Instant now) {
    return notificationRepository.claimForSending(
        key, now, now.plus(LOCK), NotificationStatus.PROCESSING, CLAIMABLE);
  }

  @Test
  void claimForSending_shouldReturnOneAndLock_whenPending() {
    String key = saveNotification(NotificationStatus.PENDING, null);
    Instant now = Instant.now();

    assertThat(claim(key, now)).isEqualTo(1);

    Notification claimed = notificationRepository.findByDeduplicationKey(key).orElseThrow();
    assertThat(claimed.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    assertThat(claimed.getAttemptCount()).isEqualTo(1);
    assertThat(claimed.getLockedUntil()).isAfter(now);
  }

  @Test
  void claimForSending_shouldReturnZero_whenAlreadyClaimedAndLockNotExpired() {
    String key = saveNotification(NotificationStatus.PENDING, null);
    Instant now = Instant.now();

    assertThat(claim(key, now)).isEqualTo(1);
    // A second consumer arriving while the first is still sending must lose.
    assertThat(claim(key, now.plusSeconds(1))).isZero();
  }

  @Test
  void claimForSending_shouldReturnOne_whenFailed() {
    String key = saveNotification(NotificationStatus.FAILED, null);

    assertThat(claim(key, Instant.now())).isEqualTo(1);
  }

  @Test
  void claimForSending_shouldReturnOne_whenProcessingLockExpired() {
    Instant now = Instant.now();
    String key = saveNotification(NotificationStatus.PROCESSING, now.minusSeconds(60));

    assertThat(claim(key, now)).isEqualTo(1);
  }

  @Test
  void claimForSending_shouldReturnZero_whenSent() {
    String key = saveNotification(NotificationStatus.SENT, null);

    assertThat(claim(key, Instant.now())).isZero();
  }
}
