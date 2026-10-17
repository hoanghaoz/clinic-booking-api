package com.se100.clinic.catalog;

import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medical_packages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalPackage extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Column(name = "name", nullable = false, length = 200)
  private String name;
}
