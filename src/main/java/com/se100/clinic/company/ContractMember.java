package com.se100.clinic.company;

import com.se100.clinic.company.enums.ContractMemberStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "contract_members",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_contract_members_package_patient",
          columnNames = {"contract_package_id", "patient_id"}),
      @UniqueConstraint(
          name = "uq_contract_members_package_employee_code",
          columnNames = {"contract_package_id", "employee_code"})
    },
    indexes = @Index(name = "idx_contract_members_patient", columnList = "patient_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContractMember extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contract_package_id", nullable = false)
  private ContractPackage contractPackage;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "employee_code", nullable = false, length = 50)
  private String employeeCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'ENROLLED'")
  private ContractMemberStatus status = ContractMemberStatus.ENROLLED;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private Long uploadedByUserId;
}
