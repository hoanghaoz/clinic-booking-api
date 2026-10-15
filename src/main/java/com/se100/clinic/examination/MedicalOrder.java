package com.se100.clinic.examination;

import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalResultStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "medical_orders",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_medical_orders_visit_source_item_occurrence",
            columnNames = {"visit_id", "source", "item_id", "occurrence_no"}),
    indexes = {
      @Index(
          name = "idx_medical_orders_room_status_queued",
          columnList = "room_id, status, queued_at"),
      @Index(name = "idx_medical_orders_performed_visit", columnList = "performed_visit_id")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "visit_id", nullable = false)
  private MedicalVisit visit;

  @Column(name = "item_id", nullable = false)
  private Long itemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private MedicalOrderSource source;

  @Column(name = "package_version_id")
  private Long packageVersionId;

  @Column(name = "service_id")
  private Long serviceId;

  @Column(name = "occurrence_no", nullable = false)
  @ColumnDefault("1")
  private int occurrenceNo = 1;

  @Column(name = "ordered_by_doctor_id")
  private Long orderedByDoctorId;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "performed_visit_id")
  private Long performedVisitId;

  @Column(name = "item_name_snapshot", nullable = false, length = 200)
  private String itemNameSnapshot;

  @Column(name = "instruction_snapshot", columnDefinition = "text")
  private String instructionSnapshot;

  @Column(name = "unit_price_snapshot", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPriceSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'ORDERED'")
  private MedicalOrderStatus status = MedicalOrderStatus.ORDERED;

  @Column(name = "queue_number")
  private Integer queueNumber;

  @Column(name = "queued_at", columnDefinition = "timestamptz")
  private Instant queuedAt;

  @Column(name = "deferred_reason", columnDefinition = "text")
  private String deferredReason;

  @Column(name = "ordered_at", nullable = false, columnDefinition = "timestamptz")
  private Instant orderedAt;

  @Column(name = "started_at", columnDefinition = "timestamptz")
  private Instant startedAt;

  @Column(name = "completed_at", columnDefinition = "timestamptz")
  private Instant completedAt;

  @Column(name = "result_text", columnDefinition = "text")
  private String resultText;

  @Column(name = "result_conclusion", columnDefinition = "text")
  private String resultConclusion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_values", columnDefinition = "jsonb")
  private String resultValues;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_files", columnDefinition = "jsonb")
  private String resultFiles;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_status")
  private MedicalResultStatus resultStatus;

  @Column(name = "performed_by_staff_id")
  private Long performedByStaffId;

  @Column(name = "performed_by_doctor_id")
  private Long performedByDoctorId;

  @Column(name = "verified_by_staff_id")
  private Long verifiedByStaffId;

  @Column(name = "verified_by_doctor_id")
  private Long verifiedByDoctorId;

  @Column(name = "performed_at", columnDefinition = "timestamptz")
  private Instant performedAt;

  @Column(name = "verified_at", columnDefinition = "timestamptz")
  private Instant verifiedAt;

  @Column(name = "released_at", columnDefinition = "timestamptz")
  private Instant releasedAt;
}
