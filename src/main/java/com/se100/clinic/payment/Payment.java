package com.se100.clinic.payment;

import com.se100.clinic.payment.enums.PaymentKind;
import com.se100.clinic.payment.enums.PaymentMethod;
import com.se100.clinic.payment.enums.TransactionStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "payments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_payments_provider_reference",
            columnNames = {"provider", "provider_reference"}),
    indexes = @Index(name = "idx_payments_invoice_status", columnList = "invoice_id, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "receipt_code", nullable = false, unique = true, length = 50)
  private String receiptCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private PaymentKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false)
  private PaymentMethod method;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'PENDING'")
  private TransactionStatus status = TransactionStatus.PENDING;

  @Column(name = "provider", length = 80)
  private String provider;

  @Column(name = "provider_reference", length = 150)
  private String providerReference;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
  private String idempotencyKey;

  @Column(name = "received_by_staff_id")
  private Long receivedByStaffId;

  @Column(name = "paid_at", columnDefinition = "timestamptz")
  private Instant paidAt;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
