package com.se100.clinic.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "contract_packages",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_contract_packages_contract_version",
            columnNames = {"contract_id", "package_version_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContractPackage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private CorporateContract contract;

  @Column(name = "package_version_id", nullable = false)
  private Long packageVersionId;

  @Column(name = "agreed_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal agreedPrice;

  @Column(name = "deposit_required", nullable = false, precision = 15, scale = 2)
  @ColumnDefault("0")
  private BigDecimal depositRequired = BigDecimal.ZERO;

  @Column(name = "max_participants")
  private Integer maxParticipants;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
