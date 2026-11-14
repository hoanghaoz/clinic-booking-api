package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.notification.enums.NotificationType;
import com.se100.clinic.patient.PatientDtos.PatientContact;
import com.se100.clinic.patient.PatientService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Simulates several consumers handling the SAME notification at the same moment (what {@code
 * max-concurrency} or several app instances cause) and checks that the email goes out once.
 *
 * <p>The database is real; only the patient lookup and the mail server are mocked.
 */
class EmailNotificationConcurrencyIntegrationTest extends AbstractIntegrationTest {

  private static final int CONSUMERS = 8;

  @Autowired private EmailNotificationService emailNotificationService;
  @Autowired private NotificationRepository notificationRepository;

  @MockitoBean private PatientService patientService;

  // The implementation class, not the JavaMailSender interface: the actuator mail health check
  // looks up JavaMailSenderImpl beans and the context fails to start without one.
  @MockitoBean private JavaMailSenderImpl mailSender;

  @Test
  void sendNotification_shouldSendMailOnce_whenManyConsumersHandleSameNotification()
      throws Exception {
    // Null ids on purpose: notifications.patient_id / user_id are foreign keys and no real patient
    // exists in this test.
    when(patientService.findContact(anyLong()))
        .thenReturn(Optional.of(new PatientContact(null, null, "patient@example.com", "Test")));

    AtomicInteger mailsSent = new AtomicInteger();
    // A slow send keeps the first consumer in PROCESSING while the others arrive.
    doAnswer(
            invocation -> {
              mailsSent.incrementAndGet();
              Thread.sleep(500);
              return null;
            })
        .when(mailSender)
        .send(any(SimpleMailMessage.class));

    String businessKey = "concurrency-" + UUID.randomUUID();
    NotificationRequest request =
        NotificationRequest.forAllChannels(
            NotificationType.CARD_EXPIRING, 1L, "title", "content", businessKey);

    runAtTheSameTime(CONSUMERS, () -> emailNotificationService.sendNotification(request));

    String dedupKey = "EMAIL:CARD_EXPIRING:" + businessKey;
    Notification row = notificationRepository.findByDeduplicationKey(dedupKey).orElseThrow();

    System.out.println("[concurrency-test] consumers      = " + CONSUMERS);
    System.out.println("[concurrency-test] mails sent     = " + mailsSent.get());
    System.out.println("[concurrency-test] final status   = " + row.getStatus());
    System.out.println("[concurrency-test] attempt count  = " + row.getAttemptCount());

    assertThat(mailsSent.get()).isEqualTo(1);
    assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(row.getAttemptCount()).isEqualTo(1);
    assertThat(row.getLockedUntil()).isNull();
  }

  /** Runs the task on {@code threads} threads that all start together; fails if any throws. */
  private void runAtTheSameTime(int threads, Runnable task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch allReady = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Void>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        Callable<Void> job =
            () -> {
              allReady.countDown();
              go.await();
              task.run();
              return null;
            };
        results.add(pool.submit(job));
      }
      allReady.await();
      go.countDown();
      for (Future<Void> result : results) {
        result.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
