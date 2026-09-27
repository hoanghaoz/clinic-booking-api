package com.se100.clinic.shared;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for entities that track creation/modification time.
 *
 * <p>Extend this instead of redeclaring {@code createdAt}/{@code updatedAt} in every entity. Spring
 * Data JPA Auditing fills the values (enabled in {@code com.se100.clinic.config.JpaAuditingConfig})
 * — like TypeORM's {@code @CreateDateColumn}/{@code @UpdateDateColumn}, or EF Core
 * CreatedAt/UpdatedAt conventions.
 *
 * <p>Stored as {@code timestamptz}; the Java type is {@link Instant} (an absolute point in time, no
 * zone attached), which avoids off-by-hours bugs when server and client run in different zones.
 * Displaying in {@code Asia/Ho_Chi_Minh} is the UI's job, not the storage layer's.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

  @CreatedDate
  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz")
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
  private Instant updatedAt;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
