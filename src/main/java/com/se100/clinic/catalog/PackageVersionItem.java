package com.se100.clinic.catalog;

import jakarta.persistence.Column;
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
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "package_version_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PackageVersionItem {

  @EmbeddedId private PackageVersionItemId id = new PackageVersionItemId();

  @MapsId("packageVersionId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "package_version_id", nullable = false)
  private PackageVersion packageVersion;

  @MapsId("itemId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private MedicalItem item;

  @Column(name = "item_name_snapshot", nullable = false, length = 200)
  private String itemNameSnapshot;

  @Column(name = "instruction_snapshot", columnDefinition = "text")
  private String instructionSnapshot;

  @Column(name = "quantity", nullable = false)
  @ColumnDefault("1")
  private Integer quantity = 1;

  @Column(name = "position", nullable = false)
  private Integer position;
}
