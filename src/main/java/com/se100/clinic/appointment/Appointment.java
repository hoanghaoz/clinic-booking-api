package com.se100.clinic.appointment;

import com.se100.clinic.appointment.enums.AppointmentSource;
import com.se100.clinic.appointment.enums.AppointmentStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "appointments",
    indexes = {
      @Index(name = "idx_appointments_patient_start", columnList = "patient_id, start_at"),
      @Index(name = "idx_appointments_schedule_status", columnList = "schedule_id, status"),
      @Index(name = "idx_appointments_status_hold_expires", columnList = "status, hold_expires_at")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Appointment extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "contract_member_id")
  private Long contractMemberId;

  @Column(name = "schedule_id", nullable = false)
  private Long scheduleId;

  @Column(name = "package_version_id")
  private Long packageVersionId;

  @Column(name = "service_id")
  private Long serviceId;

  @Column(name = "makeup_order_id")
  private Long makeupOrderId;

  @Column(name = "followup_visit_id")
  private Long followupVisitId;

  @Column(name = "start_at", nullable = false, columnDefinition = "timestamptz")
  private Instant startAt;

  @Column(name = "end_at", nullable = false, columnDefinition = "timestamptz")
  private Instant endAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'CONFIRMED'")
  private AppointmentStatus status = AppointmentStatus.CONFIRMED;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  @ColumnDefault("'ONLINE'")
  private AppointmentSource source = AppointmentSource.ONLINE;

  @Column(name = "price_snapshot", nullable = false, precision = 15, scale = 2)
  private BigDecimal priceSnapshot;

  @Column(name = "deposit_required", nullable = false, precision = 15, scale = 2)
  @ColumnDefault("0")
  private BigDecimal depositRequired = BigDecimal.ZERO;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "cancellation_policy_snapshot", nullable = false, columnDefinition = "jsonb")
  private String cancellationPolicySnapshot;

  @Column(name = "preparation_snapshot", columnDefinition = "text")
  private String preparationSnapshot;

  @Column(name = "qr_token_hash", unique = true, length = 128)
  private String qrTokenHash;

  @Column(name = "hold_expires_at", columnDefinition = "timestamptz")
  private Instant holdExpiresAt;

  @Column(name = "note", columnDefinition = "text")
  private String note;

  @Column(name = "created_by_staff_id")
  private Long createdByStaffId;
}
