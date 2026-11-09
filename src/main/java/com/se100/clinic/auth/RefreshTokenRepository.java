package com.se100.clinic.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select rt from RefreshToken rt join fetch rt.user where rt.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update RefreshToken rt
      set rt.revokedAt = :revokedAt, rt.updatedAt = :revokedAt
      where rt.user.id = :userId and rt.revokedAt is null
      """)
  int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
