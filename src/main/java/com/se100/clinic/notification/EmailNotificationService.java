package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.patient.PatientDtos.PatientContact;
import com.se100.clinic.patient.PatientService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Delivers a notification by email. Unlike in-app, sending can fail, so it goes in three steps:
 *
 * <ol>
 *   <li>Make sure a PENDING row exists for the dedup key (the unique key stops duplicates).
 *   <li>Claim the row atomically (PROCESSING + a lock). Only the consumer that wins the claim may
 *       send, so two consumers never send the same mail at the same time.
 *   <li>Send the mail, then mark the row SENT, or FAILED so the message is retried.
 * </ol>
 *
 * <p>One duplicate is still possible and cannot be removed with SMTP: the mail is sent but the app
 * dies before the row is marked SENT. After the lock expires, the redelivered message sends again.
 */
@Service
public class EmailNotificationService implements NotificationStrategy {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

  /**
   * How long a claim stays valid. Must be well above the worst-case time to send one mail (the SMTP
   * timeouts in application.yml are 5 seconds each), or a slow send could be claimed twice.
   */
  private static final Duration LOCK_DURATION = Duration.ofMinutes(3);

  /**
   * States a notification may be claimed from; PROCESSING is claimable only once its lock expires.
   */
  private static final List<NotificationStatus> CLAIMABLE =
      List.of(NotificationStatus.PENDING, NotificationStatus.FAILED);

  private final NotificationRepository notificationRepository;
  private final PatientService patientService;
  private final JavaMailSender mailSender;
  private final Clock clock;
  private final String from;

  EmailNotificationService(
      NotificationRepository notificationRepository,
      PatientService patientService,
      JavaMailSender mailSender,
      Clock clock,
      @Value("${app.notification.mail-from:no-reply@clinic.local}") String from) {
    this.notificationRepository = notificationRepository;
    this.patientService = patientService;
    this.mailSender = mailSender;
    this.clock = clock;
    this.from = from;
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void sendNotification(NotificationRequest request) {
    PatientContact contact =
        patientService
            .findContact(request.patientId())
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.NOT_FOUND, "Không tìm thấy bệnh nhân"));

    if (contact.email() == null || contact.email().isBlank()) {
      log.info("Skip email notification: patient {} has no email", contact.patientId());
      return;
    }

    String dedupKey = buildDedupKey(request);
    ensureRowExists(request, contact, dedupKey);

    if (!claim(dedupKey)) {
      log.debug("Skip email notification: already sent or being sent for key {}", dedupKey);
      return;
    }

    // Reload: the claim changed the row, so the copy we had before it is stale. Saving a stale copy
    // would overwrite the attempt count and the lock.
    Notification notification =
        notificationRepository.findByDeduplicationKey(dedupKey).orElseThrow();

    try {
      mailSender.send(buildMail(notification));
      markSent(notification);
    } catch (MailException e) {
      markFailed(notification, e);
      // Rethrow so the message is retried (and dead-lettered once retries run out).
      throw e;
    }
  }

  /**
   * Creates the PENDING row if it is missing. Losing the insert race to another consumer is fine.
   */
  private void ensureRowExists(
      NotificationRequest request, PatientContact contact, String dedupKey) {
    if (notificationRepository.existsByDeduplicationKey(dedupKey)) {
      return;
    }
    try {
      notificationRepository.save(newPending(request, contact, dedupKey));
    } catch (DataIntegrityViolationException e) {
      log.debug("Another consumer created the row for key {} first", dedupKey);
    }
  }

  /**
   * @return true if this consumer now owns the row and may send the mail.
   */
  private boolean claim(String dedupKey) {
    Instant now = clock.instant();
    int claimed =
        notificationRepository.claimForSending(
            dedupKey, now, now.plus(LOCK_DURATION), NotificationStatus.PROCESSING, CLAIMABLE);
    return claimed == 1;
  }

  private void markSent(Notification notification) {
    notification.setStatus(NotificationStatus.SENT);
    notification.setSentAt(clock.instant());
    notification.setLastError(null);
    notification.setLockedUntil(null);
    notificationRepository.save(notification);
  }

  private void markFailed(Notification notification, MailException e) {
    notification.setStatus(NotificationStatus.FAILED);
    // Only the exception type: SMTP messages can contain the recipient address.
    notification.setLastError(e.getClass().getSimpleName());
    notification.setLockedUntil(null);
    notificationRepository.save(notification);
  }

  private Notification newPending(
      NotificationRequest request, PatientContact contact, String dedupKey) {
    Instant now = clock.instant();
    Notification notification = new Notification();
    notification.setUserId(contact.userId());
    notification.setPatientId(contact.patientId());
    notification.setChannel(NotificationChannel.EMAIL);
    notification.setRecipientEmail(contact.email());
    notification.setType(request.type());
    notification.setTitle(request.title());
    notification.setContent(request.content());
    notification.setDeduplicationKey(dedupKey);
    notification.setStatus(NotificationStatus.PENDING);
    notification.setScheduledAt(now);
    notification.setNextAttemptAt(now);
    return notification;
  }

  private SimpleMailMessage buildMail(Notification notification) {
    SimpleMailMessage mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(notification.getRecipientEmail());
    mail.setSubject(notification.getTitle());
    mail.setText(notification.getContent());
    return mail;
  }

  private String buildDedupKey(NotificationRequest request) {
    return String.join(
        ":", NotificationChannel.EMAIL.name(), request.type().name(), request.dedupKey());
  }
}
