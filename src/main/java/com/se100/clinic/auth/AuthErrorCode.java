package com.se100.clinic.auth;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
  INVALID_CREDENTIALS;

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }
}
