package com.se100.clinic.shared;

/**
 * The 5 system roles, from {@code docs/de-tai.md} section 2.
 *
 * <p>Currently only referenced by the {@code SecurityConfig} skeleton. There is no account table or
 * login yet — that arrives with the {@code identity} module (see docs/setup/DECISIONS.md #5).
 */
public enum Role {
  PATIENT,
  DOCTOR,
  MEDICAL_STAFF,
  CORPORATE,
  ADMIN
}
