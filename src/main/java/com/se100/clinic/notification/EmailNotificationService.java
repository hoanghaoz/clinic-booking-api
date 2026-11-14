package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationChannel;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationStrategy {

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void sendNotification(NotificationRequest request) {
    // TODO: send the email through JavaMailSender and record it in the notifications table.
  }
}
