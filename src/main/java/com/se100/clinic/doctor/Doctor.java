package com.se100.clinic.doctor;

import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import com.se100.clinic.shared.PageParams;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Doctor profile (BacSi). The login account lives in the {@code auth} module and is referenced by
 * {@code userId} only (no JPA relation across modules).
 *
 * <p>{@code specialities} is a relation INSIDE this module, so it is mapped: the doctor owns its
 * {@link DoctorSpeciality} rows (cascade + orphanRemoval), and the service changes them only via
 * {@link #assignSpecialty}/{@link #unassignSpecialty}.
 */
@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Doctor extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  /** Business code shown to staff, e.g. {@code "BS001"}. Cannot be changed after creation. */
  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private EmployeeStatus status;

  @Column(name = "degree", nullable = false, length = 100)
  private String degree;

  // BatchSize: a page of doctors loads all their specialty links in one query, not one per doctor.
  @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
  @BatchSize(size = PageParams.MAX_SIZE)
  @Setter(AccessLevel.NONE)
  private Set<DoctorSpeciality> specialities = new LinkedHashSet<>();

  Doctor(Long userId, String code, String name, String degree) {
    this.userId = userId;
    this.code = code;
    this.name = name;
    this.degree = degree;
    this.status = EmployeeStatus.WORKING;
  }

  boolean isWorking() {
    return status == EmployeeStatus.WORKING;
  }

  boolean hasSpecialty(Long specialtyId) {
    return specialities.stream().anyMatch(link -> link.getSpecialty().getId().equals(specialtyId));
  }

  void assignSpecialty(Specialty specialty) {
    if (!hasSpecialty(specialty.getId())) {
      specialities.add(new DoctorSpeciality(this, specialty));
    }
  }

  void unassignSpecialty(Long specialtyId) {
    specialities.removeIf(link -> link.getSpecialty().getId().equals(specialtyId));
  }
}
