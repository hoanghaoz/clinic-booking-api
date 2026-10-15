package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "medical_services")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalService extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "price", nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

  @Column(name = "duration_minutes", nullable = false)
  private Integer durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(name = "target", nullable = false)
  @ColumnDefault("'BOTH'")
  private TargetGender target = TargetGender.BOTH;

  @Column(name = "min_age")
  private Integer minAge;

  @Column(name = "max_age")
  private Integer maxAge;

  @Column(name = "instruction", columnDefinition = "text")
  private String instruction;
}
