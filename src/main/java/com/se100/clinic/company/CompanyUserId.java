package com.se100.clinic.company;

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
class CompanyUserId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;
}
