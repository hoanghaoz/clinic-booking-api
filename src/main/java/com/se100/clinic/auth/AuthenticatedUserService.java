package com.se100.clinic.auth;

import com.se100.clinic.auth.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolve the authenticated principal to an internal user id for ownership checks. */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public Optional<Long> userIdForPrincipal(String principalName) {
    List<Long> matches =
        entityManager
            .createQuery(
                "select u.id from User u where (u.username = :name or u.email = :name) and u.status = :status",
                Long.class)
            .setParameter("name", principalName)
            .setParameter("status", UserStatus.ACTIVE)
            .getResultList();
    return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
  }

  @Transactional(readOnly = true)
  public Optional<AuthDtos.AccountIdentity> identityForSubject(String subject) {
    UUID publicId;
    try {
      publicId = UUID.fromString(subject);
    } catch (IllegalArgumentException | NullPointerException invalid) {
      return Optional.empty();
    }
    return entityManager
        .createQuery(
            "select u from User u where u.publicId = :id and u.status = :status", User.class)
        .setParameter("id", publicId)
        .setParameter("status", UserStatus.ACTIVE)
        .getResultStream()
        .map(
            user ->
                new AuthDtos.AccountIdentity(
                    user.getId(), user.getUsername(), user.getRole().name()))
        .findFirst();
  }
}
