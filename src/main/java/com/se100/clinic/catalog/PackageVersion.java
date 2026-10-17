package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "package_versions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_package_versions_package_version",
            columnNames = {"package_id", "version"}))
// No @Index for package_id: the unique (package_id, version) index already covers it.
// idx_package_versions_current is a partial index (WHERE effective_to IS NULL); JPA cannot
// express that, so it only exists in the Flyway migration.
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PackageVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "package_id", nullable = false)
  private MedicalPackage medicalPackage;

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "target", nullable = false)
  @ColumnDefault("'BOTH'")
  private TargetGender target = TargetGender.BOTH;

  @Column(name = "min_age")
  private Integer minAge;

  @Column(name = "max_age")
  private Integer maxAge;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "instruction", columnDefinition = "text")
  private String instruction;

  @Column(name = "version", nullable = false, length = 30)
  private String version;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "class", nullable = false)
  private PackageClass packageClass;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PackageStatus status;

  @Column(name = "consultation_minutes", nullable = false)
  private Integer consultationMinutes;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
