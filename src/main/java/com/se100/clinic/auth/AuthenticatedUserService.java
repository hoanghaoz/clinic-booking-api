package com.se100.clinic.auth;

import jakarta.persistence.EntityManager;
import java.util.Optional;
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
    return entityManager
        .createQuery(
            "select u.id from User u where u.username = :name or u.email = :name", Long.class)
        .setParameter("name", principalName)
        .getResultStream()
        .findFirst();
  }
}
