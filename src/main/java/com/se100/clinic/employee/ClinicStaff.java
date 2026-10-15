package com.se100.clinic.employee;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clinic_staffs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ClinicStaff {

  @EmbeddedId private ClinicStaffId id;

  @MapsId("staffId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "staff_id", nullable = false)
  private Staff staff;
}
