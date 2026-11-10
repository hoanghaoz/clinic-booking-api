package com.se100.clinic.patient.enums;

/** Computed state of a patient card; never stored. */
public enum PatientCardStatus {
  ACTIVE,
  /** Still valid but expires within 30 days (the renewal reminder window in the spec). */
  EXPIRING_SOON,
  EXPIRED,
  REVOKED
}
