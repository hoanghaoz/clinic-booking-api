package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.notification.NotificationDtos.MarkAllReadResponse;
import com.se100.clinic.notification.NotificationDtos.NotificationResponse;
import com.se100.clinic.notification.NotificationDtos.UnreadCountResponse;
import com.se100.clinic.notification.enums.NotificationType;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import com.se100.clinic.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP-contract test of the notification endpoints with a mocked service. */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  private static final String BASE = "/api/v1/notifications";
  private static final Long USER_ID = 7L;
  private static final UUID PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final TestingAuthenticationToken AUTH =
      new TestingAuthenticationToken("patient", "n/a", "ROLE_PATIENT");

  @Mock private NotificationInboxService service;
  @Mock private AuthenticatedUserService users;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(users.userIdForPrincipal("patient")).thenReturn(Optional.of(USER_ID));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new NotificationController(service, users))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static NotificationResponse response(Instant readAt) {
    return new NotificationResponse(
        PUBLIC_ID,
        NotificationType.CARD_EXPIRING,
        "Thẻ sắp hết hạn",
        "Nội dung",
        Instant.parse("2026-11-27T00:00:00Z"),
        readAt);
  }

  @Test
  void list_shouldReturnDataAndMeta_newestFirstByDefault() throws Exception {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.list(eq(USER_ID), eq(false), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(response(null)), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get(BASE).principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].publicId").value(PUBLIC_ID.toString()))
        .andExpect(jsonPath("$.data[0].type").value("CARD_EXPIRING"))
        .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-27T00:00:00Z"))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.totalElements").value(1));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
  }

  @Test
  void list_shouldPassUnreadOnlyToService() throws Exception {
    when(service.list(eq(USER_ID), eq(true), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(get(BASE + "?unreadOnly=true").principal(AUTH)).andExpect(status().isOk());

    verify(service).list(eq(USER_ID), eq(true), any());
  }

  @Test
  void list_shouldReturn400_whenSortFieldNotAllowed() throws Exception {
    mockMvc
        .perform(get(BASE + "?sort=title").principal(AUTH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(service);
  }

  @Test
  void list_shouldReturn401_whenAccountNoLongerActive() throws Exception {
    when(users.userIdForPrincipal("patient")).thenReturn(Optional.empty());

    mockMvc
        .perform(get(BASE).principal(AUTH))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

    verifyNoInteractions(service);
  }

  @Test
  void unreadCount_shouldReturnCount() throws Exception {
    when(service.countUnread(USER_ID)).thenReturn(new UnreadCountResponse(3));

    mockMvc
        .perform(get(BASE + "/unread-count").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(3));
  }

  @Test
  void markRead_shouldReturnUpdatedNotification() throws Exception {
    when(service.markRead(USER_ID, PUBLIC_ID))
        .thenReturn(response(Instant.parse("2026-11-27T03:00:00Z")));

    mockMvc
        .perform(post(BASE + "/" + PUBLIC_ID + "/read").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readAt").value("2026-11-27T03:00:00Z"));
  }

  @Test
  void markRead_shouldReturn404_whenNotFound() throws Exception {
    when(service.markRead(USER_ID, PUBLIC_ID))
        .thenThrow(
            new NotFoundException(
                NotificationErrorCode.NOTIFICATION_NOT_FOUND, "Không tìm thấy thông báo"));

    mockMvc
        .perform(post(BASE + "/" + PUBLIC_ID + "/read").principal(AUTH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
  }

  @Test
  void markAllRead_shouldReturnUpdatedCount() throws Exception {
    when(service.markAllRead(USER_ID)).thenReturn(new MarkAllReadResponse(2));

    mockMvc
        .perform(post(BASE + "/read-all").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updated").value(2));
  }
}
