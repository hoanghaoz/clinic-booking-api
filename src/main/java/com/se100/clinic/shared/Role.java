package com.se100.clinic.shared;

/**
 * The 5 system roles, from {@code docs/de-tai.md} section 2.
 *
 * <p>Legacy domain vocabulary. Authentication uses the persisted {@code auth.enums.UserRole}; its
 * COMPANY role corresponds to CORPORATE here.
 */
public enum Role {
  PATIENT,
  DOCTOR,
  MEDICAL_STAFF,
  CORPORATE,
  ADMIN
}
