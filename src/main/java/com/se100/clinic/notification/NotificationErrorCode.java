package com.se100.clinic.notification;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Business error codes of the {@code notification} module. */
enum NotificationErrorCode implements ErrorCode {
  /** Unknown id, or a notification owned by someone else (not told apart on purpose). */
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND);

  private final HttpStatus status;

  NotificationErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
