package com.se100.clinic.company;

import com.se100.clinic.company.enums.CorporateContractStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "corporate_contracts",
    indexes =
        @Index(name = "idx_corporate_contracts_company_status", columnList = "company_id, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CorporateContract extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "starts_on", nullable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false)
  private LocalDate endsOn;

  @Column(name = "booking_priority", nullable = false)
  @ColumnDefault("0")
  private int bookingPriority = 0;

  @Column(name = "payment_terms", columnDefinition = "text")
  private String paymentTerms;

  @Column(name = "note", columnDefinition = "text")
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'DRAFT'")
  private CorporateContractStatus status = CorporateContractStatus.DRAFT;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;
}
