package com.se100.clinic.notification.listeners;

import com.se100.clinic.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class CardExpiryListener {

  private final Logger logger = Logger.getLogger(CardExpiryListener.class.getName());

  @RabbitListener(queues = RabbitMQConfig.QUEUE)
  public void handleCardExpiryNotification(String message) {
    // Handle the card expiry notification message
    logger.info("Received card expiry notification: " + message);
  }
}
