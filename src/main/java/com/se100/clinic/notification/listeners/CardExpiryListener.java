package com.se100.clinic.notification.listeners;

import com.se100.clinic.config.RabbitMQConfig;
import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import java.util.logging.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CardExpiryListener {

  private final Logger logger = Logger.getLogger(CardExpiryListener.class.getName());

  @RabbitListener(queues = RabbitMQConfig.QUEUE)
  public void handleCardExpiryNotification(CardExpiryReminderMessage message) {
    // Handle the card expiry notification message
    logger.info("Received card expiry notification: " + message);
  }
}
