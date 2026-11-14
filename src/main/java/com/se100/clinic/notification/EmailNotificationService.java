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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Delivers a notification by email. Unlike in-app, sending can fail, so the row is saved as PENDING
 * first (this also claims the dedup key), then the mail is sent, then the row becomes SENT. A
 * redelivered message finds the row: SENT means done, anything else is sent again.
 */
@Service
public class EmailNotificationService implements NotificationStrategy {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

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
    // If two deliveries race on the insert, the unique key rejects one; its message is retried and
    // then finds the row.
    Notification notification =
        notificationRepository
            .findByDeduplicationKey(dedupKey)
            .orElseGet(() -> notificationRepository.save(newPending(request, contact, dedupKey)));

    if (notification.getStatus() == NotificationStatus.SENT) {
      log.debug("Skip email notification: already sent for key {}", dedupKey);
      return;
    }

    notification.setAttemptCount(notification.getAttemptCount() + 1);
    try {
      mailSender.send(buildMail(notification));
      notification.setStatus(NotificationStatus.SENT);
      notification.setSentAt(clock.instant());
      notification.setLastError(null);
      notificationRepository.save(notification);
    } catch (MailException e) {
      notification.setStatus(NotificationStatus.FAILED);
      // Only the exception type: SMTP messages can contain the recipient address.
      notification.setLastError(e.getClass().getSimpleName());
      notificationRepository.save(notification);
      // Rethrow so the message is retried (and dead-lettered once retries run out).
      throw e;
    }
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
