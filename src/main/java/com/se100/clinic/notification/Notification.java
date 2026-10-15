package com.se100.clinic.notification;

import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.notification.enums.NotificationType;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(
          name = "idx_notifications_user_channel_read_created",
          columnList = "user_id, channel, read_at, created_at"),
      @Index(
          name = "idx_notifications_status_next_attempt",
          columnList = "status, next_attempt_at"),
      @Index(
          name = "idx_notifications_patient_type_status",
          columnList = "patient_id, type, status"),
      @Index(name = "idx_notifications_appointment", columnList = "appointment_id")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Notification extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "patient_id")
  private Long patientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private NotificationChannel channel;

  @Column(name = "recipient_email", length = 254)
  private String recipientEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private NotificationType type;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "appointment_id")
  private Long appointmentId;

  @Column(name = "waitlist_id")
  private Long waitlistId;

  @Column(name = "medical_visit_id")
  private Long medicalVisitId;

  @Column(name = "invoice_id")
  private Long invoiceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "data", columnDefinition = "jsonb")
  private String data;

  @Column(name = "deduplication_key", nullable = false, unique = true, length = 200)
  private String deduplicationKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'PENDING'")
  private NotificationStatus status = NotificationStatus.PENDING;

  @Column(name = "scheduled_at", nullable = false, columnDefinition = "timestamptz")
  private Instant scheduledAt;

  @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamptz")
  private Instant nextAttemptAt;

  @Column(name = "attempt_count", nullable = false)
  @ColumnDefault("0")
  private int attemptCount = 0;

  @Column(name = "max_attempts", nullable = false)
  @ColumnDefault("5")
  private int maxAttempts = 5;

  @Column(name = "locked_until", columnDefinition = "timestamptz")
  private Instant lockedUntil;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "sent_at", columnDefinition = "timestamptz")
  private Instant sentAt;

  @Column(name = "read_at", columnDefinition = "timestamptz")
  private Instant readAt;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;
}
