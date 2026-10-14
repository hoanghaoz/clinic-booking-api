package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medical_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalItem extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private MedicalItemType type;

  @Column(name = "price", nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "instruction", columnDefinition = "text")
  private String instruction;
}
