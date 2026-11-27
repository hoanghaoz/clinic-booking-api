/**
 * Patient reception and room routing flow (REC-01..REC-04).
 *
 * <p>Owns counter lookup, check-in validation, creation of {@code medical_visits} and initial
 * {@code medical_orders}, and routing patients into clinical/paraclinical rooms. Delegates patient
 * lookup, medical history, card validity, and QR verification to the public services of {@code
 * patient}, {@code appointment}, {@code employee}, and {@code auth}.
 */
package com.se100.clinic.reception;
