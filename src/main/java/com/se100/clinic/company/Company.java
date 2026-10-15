package com.se100.clinic.company;

import com.se100.clinic.company.enums.CompanyStatus;
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
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Company extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "tax_code", nullable = false, unique = true, length = 30)
  private String taxCode;

  @Column(name = "address", nullable = false, columnDefinition = "text")
  private String address;

  @Column(name = "contact_name", length = 150)
  private String contactName;

  @Column(name = "contact_email", length = 254)
  private String contactEmail;

  @Column(name = "contact_phone", length = 25)
  private String contactPhone;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'ACTIVE'")
  private CompanyStatus status = CompanyStatus.ACTIVE;
}
