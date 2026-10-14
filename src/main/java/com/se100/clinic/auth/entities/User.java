package com.se100.clinic.auth.entities;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseAuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private UUID publicId = UUID.randomUUID();

  @Column(unique = true, nullable = false)
  private String username;

  @Column(nullable = false)
  private String passwordHashed;
  @Column(nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private UserRole role;
}
