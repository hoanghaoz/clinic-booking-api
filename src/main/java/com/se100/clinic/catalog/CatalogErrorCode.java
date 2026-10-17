package com.se100.clinic.catalog;

import com.se100.clinic.shared.ErrorCode;
import org.springframework.http.HttpStatus;

enum CatalogErrorCode implements ErrorCode {
  ;

  private final HttpStatus status;

  CatalogErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
