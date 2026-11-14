package com.se100.clinic.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
  public static final String EXCHANGE = "notification.exchange";
  public static final String QUEUE = "notification.card-expiry.queue";
  public static final String ROUTING_KEY = "card.expiring";
  public static final String DLX = "notification.dlx";
  public static final String DLQ = "notification.card-expiry.dlq";

  @Bean
  DirectExchange notificationExchange() {
    return new DirectExchange(EXCHANGE);
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DLX);
  }

  // define a queue that receive message from the exchange, and if the message is rejected, it will
  // be sent to the dead letter exchange
  @Bean
  Queue cardExpiryQueue() {
    return QueueBuilder.durable(QUEUE)
        .quorum()
        .deadLetterExchange(DLX)
        .deadLetterRoutingKey(ROUTING_KEY)
        .build();
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(DLQ).quorum().build();
  }

  @Bean
  Binding cardExpiryBinding() {
    return BindingBuilder.bind(cardExpiryQueue()).to(notificationExchange()).with(ROUTING_KEY);
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(ROUTING_KEY);
  }

  // Send/receive message bodies as JSON instead of Java serialization.
  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }
}
