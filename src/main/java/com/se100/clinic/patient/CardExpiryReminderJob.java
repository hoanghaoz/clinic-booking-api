package com.se100.clinic.patient;

import com.se100.clinic.config.RabbitMQConfig;
import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
class CardExpiryReminderJob {

  private static final Logger log = LoggerFactory.getLogger(CardExpiryReminderJob.class);
  private static final int PAGE_SIZE = 20;
  private final RabbitTemplate rabbitTemplate;
  private final PatientRepository patientRepository;
  private final Clock clock;

  @Scheduled(cron = "0 0 7 * * *", zone = "${app.timezone}")
  void remindExpiringCards() {
    log.info("CardExpiryReminderJob: looking for expiring cards to remind starting from now");

    int page = 0;
    Slice<Patient> slice;
    Instant from = Instant.now(clock);
    Instant to = from.plus(Duration.ofDays(30));
    do {
      // Sort by id so every page is stable; page++ moves to the next page on each loop.
      Pageable pageable = PageRequest.of(page++, PAGE_SIZE, Sort.by("id"));
      slice = patientRepository.findPatientCardExpiresToRemind(from, to, pageable);

      for(var patient : slice.getContent()) {
        log.info("CardExpiryReminderJob: reminding patient {} of expiring card at {}", patient.getId(), patient.getCardExpiresAt());
        try{
          rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_KEY,
            CardExpiryReminderMessage.of(patient.getId(), patient.getCardExpiresAt()));
        }
        catch(AmqpException e){
          log.error("CardExpiryReminderJob: failed to send reminder for patient {}: {}", patient.getId(), e.getMessage());
        }
      }
    }
    while (slice.hasNext());
  }
}
