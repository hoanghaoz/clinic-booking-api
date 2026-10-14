package com.se100.clinic.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_tokens_user_revoked", columnList = "user_id, revoked_at"),
      @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
    })
class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 128)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
  private Instant expiresAt;

  @Column(name = "revoked_at", columnDefinition = "timestamptz")
  private Instant revokedAt;

  @CreatedDate
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
