package com.se100.clinic.patient;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import java.util.Arrays;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Turns the authenticated principal into the {@link PatientActor} the patient services expect.
 * Controllers stay thin; the services decide what each role may do.
 */
@Component
class PatientActorResolver {

  private static final String ROLE_PREFIX = "ROLE_";

  private final AuthenticatedUserService users;

  PatientActorResolver(AuthenticatedUserService users) {
    this.users = users;
  }

  PatientActor resolve(Authentication authentication) {
    if (authentication == null) {
      throw authRequired();
    }
    Long userId =
        users
            .userIdForPrincipal(authentication.getName())
            .orElseThrow(PatientActorResolver::authRequired);
    UserRole role =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(ROLE_PREFIX))
            .map(authority -> authority.substring(ROLE_PREFIX.length()))
            .flatMap(name -> Arrays.stream(UserRole.values()).filter(r -> r.name().equals(name)))
            .findFirst()
            .orElseThrow(PatientActorResolver::authRequired);
    return new PatientActor(userId, role);
  }

  private static BusinessException authRequired() {
    return new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập");
  }
}
