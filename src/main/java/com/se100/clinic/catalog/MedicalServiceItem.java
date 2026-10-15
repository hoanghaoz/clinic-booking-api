package com.se100.clinic.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "medical_service_items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalServiceItem {

  @EmbeddedId private MedicalServiceItemId id = new MedicalServiceItemId();

  @MapsId("serviceId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private MedicalService service;

  @MapsId("itemId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private MedicalItem item;

  @Column(name = "quantity", nullable = false)
  @ColumnDefault("1")
  private Integer quantity = 1;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
