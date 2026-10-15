package com.se100.clinic.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
class PackageVersionItemId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "package_version_id", nullable = false)
  private Long packageVersionId;

  @Column(name = "item_id", nullable = false)
  private Long itemId;
}
