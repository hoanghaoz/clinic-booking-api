package com.se100.clinic.payment;

import com.se100.clinic.payment.enums.InvoicePayer;
import com.se100.clinic.payment.enums.InvoiceStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "invoices",
    indexes =
        @Index(name = "idx_invoices_appointment_status", columnList = "appointment_id, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Invoice extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "appointment_id", nullable = false)
  private Long appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payer", nullable = false)
  @ColumnDefault("'PATIENT'")
  private InvoicePayer payer = InvoicePayer.PATIENT;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "contract_id")
  private Long contractId;

  @Column(name = "buyer_name_snapshot", nullable = false, length = 200)
  private String buyerNameSnapshot;

  @Column(name = "buyer_address_snapshot", columnDefinition = "text")
  private String buyerAddressSnapshot;

  @Column(name = "buyer_tax_code_snapshot", length = 30)
  private String buyerTaxCodeSnapshot;

  @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
  @ColumnDefault("0")
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'DRAFT'")
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Column(name = "created_by_staff_id")
  private Long createdByStaffId;

  @Column(name = "issued_at", columnDefinition = "timestamptz")
  private Instant issuedAt;

  @Column(name = "void_reason", columnDefinition = "text")
  private String voidReason;

  @Column(name = "voided_at", columnDefinition = "timestamptz")
  private Instant voidedAt;
}
