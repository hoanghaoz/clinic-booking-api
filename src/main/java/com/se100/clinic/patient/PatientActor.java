package com.se100.clinic.patient;

import com.se100.clinic.auth.enums.UserRole;

/**
 * The authenticated caller of a {@link PatientService} method. The controller resolves it from the
 * principal (see {@code AuthenticatedUserService}); the service uses it for ownership checks.
 */
public record PatientActor(Long userId, UserRole role) {}
