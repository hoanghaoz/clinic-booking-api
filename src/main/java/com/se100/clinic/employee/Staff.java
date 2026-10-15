package com.se100.clinic.employee;

import com.se100.clinic.employee.enums.EmployeeStatus;
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

@Entity
@Table(name = "staffs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Staff extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private EmployeeStatus status;
}
