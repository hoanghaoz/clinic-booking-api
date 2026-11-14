package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;

public interface NotificationStrategy {
  void SendNotification(CardExpiryReminderMessage message);
}
