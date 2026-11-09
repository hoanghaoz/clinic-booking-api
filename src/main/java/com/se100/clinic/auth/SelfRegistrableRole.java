package com.se100.clinic.auth;

import com.se100.clinic.auth.enums.UserRole;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Doctors, medical staff, and admins are provisioned by an administrator; only {@link
 * UserRole#PATIENT} and {@link UserRole#COMPANY} may self-register. {@code null} is allowed at the
 * DTO level so the service can default to {@link UserRole#PATIENT}.
 */
@Documented
@Constraint(validatedBy = SelfRegistrableRole.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface SelfRegistrableRole {

  String message() default "Chỉ bệnh nhân (PATIENT) và doanh nghiệp (COMPANY) được phép tự đăng ký";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  final class Validator implements ConstraintValidator<SelfRegistrableRole, UserRole> {
    @Override
    public boolean isValid(UserRole value, ConstraintValidatorContext context) {
      return value == null || value == UserRole.PATIENT || value == UserRole.COMPANY;
    }
  }
}
