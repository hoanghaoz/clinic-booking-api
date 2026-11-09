package com.se100.clinic.auth;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
  EMAIL_EXISTS(HttpStatus.CONFLICT),
  USERNAME_EXISTS(HttpStatus.CONFLICT);

  private final HttpStatus status;

  AuthErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
