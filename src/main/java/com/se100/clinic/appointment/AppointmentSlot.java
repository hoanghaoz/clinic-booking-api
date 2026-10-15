package com.se100.clinic.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "appointment_slots",
    indexes = @Index(name = "idx_appointment_slots_slot", columnList = "slot_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AppointmentSlot {

  @EmbeddedId private AppointmentSlotId id;

  @MapsId("appointmentId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "appointment_id", nullable = false)
  private Appointment appointment;

  @Column(name = "reserved_at", nullable = false, columnDefinition = "timestamptz")
  private Instant reservedAt;

  @Column(name = "released_at", columnDefinition = "timestamptz")
  private Instant releasedAt;
}
