package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationChannel;

/** Delivers a {@link NotificationRequest} through one channel (email, in-app...). */
public interface NotificationStrategy {

  /** The channel this strategy handles; used as the lookup key by the dispatcher. */
  NotificationChannel channel();

  void sendNotification(NotificationRequest request);
}
