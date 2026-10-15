package com.se100.clinic.appointment;

import com.se100.clinic.appointment.enums.WaitlistStatus;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "appointment_waitlists",
    indexes =
        @Index(
            name = "idx_appointment_waitlists_queue",
            columnList = "schedule_id, status, priority, created_at"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AppointmentWaitlist extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "schedule_id", nullable = false)
  private Long scheduleId;

  @Column(name = "package_version_id")
  private Long packageVersionId;

  @Column(name = "service_id")
  private Long serviceId;

  @Column(name = "requested_start_at", nullable = false, columnDefinition = "timestamptz")
  private Instant requestedStartAt;

  @Column(name = "requested_end_at", nullable = false, columnDefinition = "timestamptz")
  private Instant requestedEndAt;

  @Column(name = "priority", nullable = false)
  @ColumnDefault("0")
  private int priority = 0;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'WAITING'")
  private WaitlistStatus status = WaitlistStatus.WAITING;

  @Column(name = "offered_appointment_id", unique = true)
  private Long offeredAppointmentId;

  @Column(name = "offer_expires_at", columnDefinition = "timestamptz")
  private Instant offerExpiresAt;

  @Column(name = "notified_at", columnDefinition = "timestamptz")
  private Instant notifiedAt;
}
