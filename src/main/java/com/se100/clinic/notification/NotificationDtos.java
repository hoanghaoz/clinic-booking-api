package com.se100.clinic.notification;

import com.se100.clinic.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Request/response records of the in-app notification API. */
public final class NotificationDtos {

  private NotificationDtos() {}

  @Schema(requiredProperties = {"publicId", "type", "title", "content", "createdAt"})
  public record NotificationResponse(
      UUID publicId,
      NotificationType type,
      String title,
      String content,
      Instant createdAt,
      @Schema(description = "Thời điểm đã đọc; null nghĩa là chưa đọc") Instant readAt) {

    static NotificationResponse from(Notification notification) {
      return new NotificationResponse(
          notification.getPublicId(),
          notification.getType(),
          notification.getTitle(),
          notification.getContent(),
          notification.getCreatedAt(),
          notification.getReadAt());
    }
  }

  @Schema(requiredProperties = {"unreadCount"})
  public record UnreadCountResponse(long unreadCount) {}

  @Schema(requiredProperties = {"updated"})
  public record MarkAllReadResponse(
      @Schema(description = "Số thông báo vừa được đánh dấu đã đọc") int updated) {}
}
