package com.se100.clinic.catalog;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

enum CatalogErrorCode implements ErrorCode {
  MEDICAL_PACKAGE_NOT_FOUND(HttpStatus.NOT_FOUND),
  MEDICAL_PACKAGE_EXISTS(HttpStatus.CONFLICT),
  MEDICAL_PACKAGE_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
  MEDICAL_PACKAGE_SPECIALTY_REQUIRED(HttpStatus.BAD_REQUEST),
  MEDICAL_PACKAGE_SPECIALTY_INACTIVE(HttpStatus.CONFLICT),
  MEDICAL_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
  MEDICAL_ITEM_EXISTS(HttpStatus.CONFLICT);

  private final HttpStatus status;

  CatalogErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
