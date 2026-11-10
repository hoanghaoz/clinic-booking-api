package com.se100.clinic.patient.enums;

/** Who recorded a medical history entry; shown instead of internal doctor / staff ids. */
public enum MedicalHistorySource {
  DOCTOR,
  STAFF,
  /** Self-declared by the patient. */
  PATIENT
}
