package com.se100.clinic.appointment;

import com.se100.clinic.appointment.enums.AppointmentAction;
import com.se100.clinic.appointment.enums.AppointmentActor;
import com.se100.clinic.appointment.enums.AppointmentStatus;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "appointment_histories",
    indexes =
        @Index(
            name = "idx_appointment_histories_appointment_created",
            columnList = "appointment_id, created_at"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AppointmentHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "appointment_id", nullable = false)
  private Appointment appointment;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false)
  private AppointmentAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor", nullable = false)
  private AppointmentActor actor;

  @Column(name = "actor_patient_id")
  private Long actorPatientId;

  @Column(name = "actor_staff_id")
  private Long actorStaffId;

  @Enumerated(EnumType.STRING)
  @Column(name = "old_status")
  private AppointmentStatus oldStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status")
  private AppointmentStatus newStatus;

  @Column(name = "old_schedule_id")
  private Long oldScheduleId;

  @Column(name = "new_schedule_id")
  private Long newScheduleId;

  @Column(name = "old_start_at", columnDefinition = "timestamptz")
  private Instant oldStartAt;

  @Column(name = "old_end_at", columnDefinition = "timestamptz")
  private Instant oldEndAt;

  @Column(name = "new_start_at", columnDefinition = "timestamptz")
  private Instant newStartAt;

  @Column(name = "new_end_at", columnDefinition = "timestamptz")
  private Instant newEndAt;

  @Column(name = "reason", columnDefinition = "text")
  private String reason;

  @CreationTimestamp
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;
}
