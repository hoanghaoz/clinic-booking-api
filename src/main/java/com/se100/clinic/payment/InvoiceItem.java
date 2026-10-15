package com.se100.clinic.payment;

import com.se100.clinic.payment.enums.InvoiceItemType;
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
    name = "invoice_items",
    indexes = {
      @Index(name = "idx_invoice_items_invoice", columnList = "invoice_id"),
      @Index(name = "idx_invoice_items_medical_order", columnList = "medical_order_id"),
      @Index(name = "idx_invoice_items_charge_key", columnList = "charge_key")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class InvoiceItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private InvoiceItemType type;

  @Column(name = "medical_order_id")
  private Long medicalOrderId;

  @Column(name = "charge_key", nullable = false, length = 150)
  private String chargeKey;

  @Column(name = "description_snapshot", nullable = false, columnDefinition = "text")
  private String descriptionSnapshot;

  @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
  @ColumnDefault("1")
  private BigDecimal quantity = BigDecimal.ONE;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
  @ColumnDefault("0")
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal lineTotal;

  @Column(name = "voided_at", columnDefinition = "timestamptz")
  private Instant voidedAt;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
