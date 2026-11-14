package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.patient.PatientDtos.PatientContact;
import com.se100.clinic.patient.PatientService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Delivers a notification in-app: inserting the row IS the delivery, the patient's app reads it
 * from the notifications table. Safe to call twice for the same event.
 */
@Service
@RequiredArgsConstructor
public class InAppNotificationService implements NotificationStrategy {

  private static final Logger log = LoggerFactory.getLogger(InAppNotificationService.class);

  private final NotificationRepository notificationRepository;
  private final PatientService patientService;
  private final Clock clock;

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.IN_APP;
  }

  @Override
  public void sendNotification(NotificationRequest request) {
    PatientContact contact =
        patientService
            .findContact(request.patientId())
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.NOT_FOUND, "Không tìm thấy bệnh nhân"));

    // A walk-in patient has no account, so there is no app feed to show the notification in.
    if (contact.userId() == null) {
      log.info("Skip in-app notification: patient {} has no account", contact.patientId());
      return;
    }

    // The channel is part of the key because the unique column is shared by all channels.
    String dedupKey = buildDedupKey(request);
    if (notificationRepository.existsByDeduplicationKey(dedupKey)) {
      log.debug("Skip in-app notification: already delivered for key {}", dedupKey);
      return;
    }

    Instant now = clock.instant();
    Notification notification = new Notification();
    notification.setUserId(contact.userId());
    notification.setPatientId(contact.patientId());
    notification.setChannel(NotificationChannel.IN_APP);
    notification.setType(request.type());
    notification.setTitle(request.title());
    notification.setContent(request.content());
    notification.setDeduplicationKey(dedupKey);
    // No separate sending step for in-app: the row is the delivery, so it is already SENT.
    notification.setStatus(NotificationStatus.SENT);
    notification.setScheduledAt(now);
    notification.setNextAttemptAt(now);
    notification.setSentAt(now);

    try {
      notificationRepository.save(notification);
    } catch (DataIntegrityViolationException e) {
      // Two deliveries of the same message raced past the exists check; the unique key wins.
      log.debug("Skip in-app notification: duplicate key {} on insert", dedupKey);
    }
  }

  private String buildDedupKey(NotificationRequest request) {
    return String.join(
        ":", NotificationChannel.IN_APP.name(), request.type().name(), request.dedupKey());
  }
}
