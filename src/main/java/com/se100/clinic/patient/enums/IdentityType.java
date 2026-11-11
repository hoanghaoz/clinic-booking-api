package com.se100.clinic.patient.enums;

/** Kind of identity document a patient registers with (docs/de-tai.md: "Số CCCD hoặc Hộ chiếu"). */
public enum IdentityType {
  /** Citizen identity card (Căn cước công dân). */
  CCCD,
  /** Old 9-digit identity card (Chứng minh nhân dân), still in circulation. */
  CMND,
  PASSPORT
}
