package com.se100.clinic.facility;

import com.se100.clinic.facility.enums.FacilityStatus;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clinics")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Clinic extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 30)
  private String code;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "address", nullable = false, columnDefinition = "text")
  private String address;

  @Column(name = "phone_number", nullable = false, length = 25)
  private String phoneNumber;

  @Column(name = "email", length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private FacilityStatus status;
}
