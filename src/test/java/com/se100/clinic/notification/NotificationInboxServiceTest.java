package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.notification.NotificationDtos.NotificationResponse;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.notification.enums.NotificationType;
import com.se100.clinic.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

  private static final Long USER_ID = 7L;
  private static final UUID PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");
  private static final Pageable PAGE = PageRequest.of(0, 20);

  @Mock private NotificationRepository repository;

  private NotificationInboxService service;

  @BeforeEach
  void setUp() {
    service = new NotificationInboxService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Notification notification(Instant readAt) {
    Notification notification = new Notification();
    notification.setPublicId(PUBLIC_ID);
    notification.setUserId(USER_ID);
    notification.setType(NotificationType.CARD_EXPIRING);
    notification.setTitle("Thẻ sắp hết hạn");
    notification.setContent("Nội dung");
    notification.setReadAt(readAt);
    return notification;
  }

  @Test
  void list_shouldReturnDeliveredInAppNotificationsOfUser() {
    when(repository.findByUserIdAndChannelAndStatus(
            USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT, PAGE))
        .thenReturn(new PageImpl<>(List.of(notification(null)), PAGE, 1));

    var page = service.list(USER_ID, false, PAGE);

    assertThat(page.getContent())
        .containsExactly(
            new NotificationResponse(
                PUBLIC_ID,
                NotificationType.CARD_EXPIRING,
                "Thẻ sắp hết hạn",
                "Nội dung",
                null,
                null));
  }

  @Test
  void list_shouldQueryOnlyUnread_whenUnreadOnly() {
    when(repository.findByUserIdAndChannelAndStatusAndReadAtIsNull(
            USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT, PAGE))
        .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

    assertThat(service.list(USER_ID, true, PAGE).getContent()).isEmpty();
  }

  @Test
  void countUnread_shouldReturnRepositoryCount() {
    when(repository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
            USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT))
        .thenReturn(3L);

    assertThat(service.countUnread(USER_ID).unreadCount()).isEqualTo(3);
  }

  @Test
  void markRead_shouldSetReadAt_whenUnread() {
    Notification unread = notification(null);
    when(repository.findByPublicIdAndUserIdAndChannelAndStatus(
            PUBLIC_ID, USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT))
        .thenReturn(Optional.of(unread));

    assertThat(service.markRead(USER_ID, PUBLIC_ID).readAt()).isEqualTo(NOW);
    assertThat(unread.getReadAt()).isEqualTo(NOW);
  }

  @Test
  void markRead_shouldKeepFirstReadAt_whenAlreadyRead() {
    Instant firstRead = NOW.minusSeconds(3600);
    when(repository.findByPublicIdAndUserIdAndChannelAndStatus(
            PUBLIC_ID, USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT))
        .thenReturn(Optional.of(notification(firstRead)));

    assertThat(service.markRead(USER_ID, PUBLIC_ID).readAt()).isEqualTo(firstRead);
  }

  @Test
  void markRead_shouldThrowNotFound_whenNotOwnedByUser() {
    when(repository.findByPublicIdAndUserIdAndChannelAndStatus(
            PUBLIC_ID, USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(USER_ID, PUBLIC_ID))
        .isInstanceOfSatisfying(
            NotFoundException.class,
            error ->
                assertThat(error.getErrorCode())
                    .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
  }

  @Test
  void markAllRead_shouldReturnNumberOfUpdatedRows() {
    when(repository.markAllRead(USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT, NOW))
        .thenReturn(2);

    assertThat(service.markAllRead(USER_ID).updated()).isEqualTo(2);
    verify(repository)
        .markAllRead(USER_ID, NotificationChannel.IN_APP, NotificationStatus.SENT, NOW);
  }
}
