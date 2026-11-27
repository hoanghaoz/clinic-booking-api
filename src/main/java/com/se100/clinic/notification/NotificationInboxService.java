package com.se100.clinic.notification;

import com.se100.clinic.notification.NotificationDtos.MarkAllReadResponse;
import com.se100.clinic.notification.NotificationDtos.NotificationResponse;
import com.se100.clinic.notification.NotificationDtos.UnreadCountResponse;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.shared.error.NotFoundException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a user can do with their own in-app notifications. Every query is scoped to the user id and
 * to delivered ({@code SENT}) rows of the {@code IN_APP} channel, so email rows and other users'
 * notifications are never visible here.
 */
@Service
public class NotificationInboxService {

  public static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");
  public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private static final NotificationChannel CHANNEL = NotificationChannel.IN_APP;
  private static final NotificationStatus DELIVERED = NotificationStatus.SENT;

  private final NotificationRepository notificationRepository;
  private final Clock clock;

  NotificationInboxService(NotificationRepository notificationRepository, Clock clock) {
    this.notificationRepository = notificationRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<NotificationResponse> list(Long userId, boolean unreadOnly, Pageable pageable) {
    Page<Notification> page =
        unreadOnly
            ? notificationRepository.findByUserIdAndChannelAndStatusAndReadAtIsNull(
                userId, CHANNEL, DELIVERED, pageable)
            : notificationRepository.findByUserIdAndChannelAndStatus(
                userId, CHANNEL, DELIVERED, pageable);
    return page.map(NotificationResponse::from);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse countUnread(Long userId) {
    return new UnreadCountResponse(
        notificationRepository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
            userId, CHANNEL, DELIVERED));
  }

  /** Idempotent: reading a notification again keeps the first read time. */
  @Transactional
  public NotificationResponse markRead(Long userId, UUID publicId) {
    Notification notification =
        notificationRepository
            .findByPublicIdAndUserIdAndChannelAndStatus(publicId, userId, CHANNEL, DELIVERED)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND, "Không tìm thấy thông báo"));
    if (notification.getReadAt() == null) {
      notification.setReadAt(clock.instant());
    }
    return NotificationResponse.from(notification);
  }

  @Transactional
  public MarkAllReadResponse markAllRead(Long userId) {
    return new MarkAllReadResponse(
        notificationRepository.markAllRead(userId, CHANNEL, DELIVERED, clock.instant()));
  }
}
