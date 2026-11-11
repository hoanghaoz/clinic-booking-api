package com.se100.clinic.patient;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import java.util.Objects;
import java.util.Set;

/** Role and ownership checks shared by the services of the patient module. */
final class PatientAccess {

  private PatientAccess() {}

  /** Throws 403 unless the actor holds one of the allowed roles (a PATIENT also needs a userId). */
  static void requireRole(PatientActor actor, Set<UserRole> allowedRoles) {
    if (actor == null
        || actor.role() == null
        || !allowedRoles.contains(actor.role())
        || (actor.role() == UserRole.PATIENT && actor.userId() == null)) {
      throw forbidden();
    }
  }

  /** A PATIENT actor may only touch their own profile; every other allowed role may touch any. */
  static void requireOwnerIfPatient(PatientActor actor, Patient patient) {
    if (actor.role() == UserRole.PATIENT && !Objects.equals(patient.getUserId(), actor.userId())) {
      throw forbidden();
    }
  }

  static BusinessException forbidden() {
    return new BusinessException(
        CommonErrorCode.FORBIDDEN, "Không có quyền thực hiện thao tác này");
  }
}
