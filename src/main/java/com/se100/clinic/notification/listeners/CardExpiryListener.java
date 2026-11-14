package com.se100.clinic.notification.listeners;

import com.se100.clinic.config.RabbitMQConfig;
import com.se100.clinic.notification.CardExpiryNotificationFactory;
import com.se100.clinic.notification.NotificationDispatcher;
import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardExpiryListener {

  private static final Logger log = LoggerFactory.getLogger(CardExpiryListener.class);

  private final CardExpiryNotificationFactory factory;
  private final NotificationDispatcher dispatcher;

  @RabbitListener(queues = RabbitMQConfig.QUEUE)
  public void handleCardExpiryNotification(CardExpiryReminderMessage message) {
    // Log only the id, not the message body.
    log.info("Received card expiry reminder for patient {}", message.patientId());
    dispatcher.dispatch(factory.create(message));
  }
}
