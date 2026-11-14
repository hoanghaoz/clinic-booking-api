package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.config.RabbitMQConfig;
import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import com.se100.clinic.patient.PatientService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Checks that a failure which can never succeed on a retry (the patient does not exist) goes to the
 * dead letter queue right away, instead of after all retries (2s + 4s + 8s).
 *
 * <p>Runs against its own RabbitMQ container (same image as docker-compose), so it needs neither a
 * broker on localhost nor a stopped {@code clinic-api} container.
 */
class CardExpiryDeadLetterIntegrationTest extends AbstractIntegrationTest {

  // Far below the 14 seconds that three retries would take.
  private static final Duration MUST_ARRIVE_WITHIN = Duration.ofSeconds(6);

  // Started once for this test class; @ServiceConnection points spring.rabbitmq.* at it.
  @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.4.0-management-alpine"));

  static {
    RABBITMQ.start();
  }

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private AmqpAdmin amqpAdmin;

  @MockitoBean private PatientService patientService;

  @Test
  void handleCardExpiryNotification_shouldDeadLetterImmediately_whenPatientNotFound() {
    when(patientService.findContact(anyLong())).thenReturn(Optional.empty());
    amqpAdmin.purgeQueue(RabbitMQConfig.DLQ, true);

    rabbitTemplate.convertAndSend(
        RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY,
        CardExpiryReminderMessage.of(999_999L, Instant.now().plus(Duration.ofDays(10))));
    long start = System.nanoTime();

    Message deadLettered = rabbitTemplate.receive(RabbitMQConfig.DLQ, 30_000);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    System.out.println("[dlq-test] reached the DLQ = " + (deadLettered != null));
    System.out.println("[dlq-test] elapsed         = " + elapsed.toMillis() + " ms");

    assertThat(deadLettered).isNotNull();
    assertThat(elapsed).isLessThan(MUST_ARRIVE_WITHIN);
  }
}
