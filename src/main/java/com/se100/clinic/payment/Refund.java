package com.se100.clinic.payment;

import com.se100.clinic.payment.enums.RefundReason;
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
    name = "refunds",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_refunds_provider_reference",
            columnNames = {"provider", "provider_reference"}),
    indexes = @Index(name = "idx_refunds_payment_status", columnList = "payment_id, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Refund {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @Column(name = "cancellation_history_id")
  private Long cancellationHistoryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false)
  private RefundReason reason;

  @Column(name = "description", columnDefinition = "text")
  private String description;

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

  @Column(name = "processed_by_staff_id")
  private Long processedByStaffId;

  @Column(name = "refunded_at", columnDefinition = "timestamptz")
  private Instant refundedAt;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
