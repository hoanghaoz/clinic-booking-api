package com.se100.clinic.schedule;

import com.se100.clinic.schedule.enums.ScheduleStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "doctor_schedules",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_doctor_schedules_doctor_date_shift",
          columnNames = {"doctor_id", "work_date", "shift_id"}),
      @UniqueConstraint(
          name = "uk_doctor_schedules_room_date_shift",
          columnNames = {"room_id", "work_date", "shift_id"})
    },
    indexes =
        @Index(name = "idx_doctor_schedules_work_date_status", columnList = "work_date, status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class DoctorSchedule extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "doctor_id", nullable = false)
  private Long doctorId;

  @Column(name = "specialty_id", nullable = false)
  private Long specialtyId;

  @Column(name = "room_id", nullable = false)
  private Long roomId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shift_id", nullable = false)
  private Shift shift;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Column(name = "general_capacity", nullable = false)
  private Integer generalCapacity;

  @Column(name = "specialist_capacity", nullable = false)
  private Integer specialistCapacity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'OPEN'")
  private ScheduleStatus status = ScheduleStatus.OPEN;
}
