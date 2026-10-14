package com.se100.clinic.auth;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class User extends BaseAuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", unique = true, nullable = false)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "username", unique = true, nullable = false, length = 100)
  private String username;

  @Column(name = "password_hashed", nullable = false, columnDefinition = "text")
  private String passwordHashed;

  @Column(name = "email", unique = true, nullable = false, length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'ACTIVE'")
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "role", nullable = false)
  @Enumerated(EnumType.STRING)
  private UserRole role;
}
