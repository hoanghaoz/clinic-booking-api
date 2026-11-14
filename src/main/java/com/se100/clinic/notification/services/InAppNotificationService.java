package com.se100.clinic.notification.services;

import com.se100.clinic.notification.NotificationStrategy;
import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import org.springframework.stereotype.Service;

@Service
public class InAppNotificationService implements NotificationStrategy {
  @Override
  public void SendNotification(CardExpiryReminderMessage message) {

  }
}
