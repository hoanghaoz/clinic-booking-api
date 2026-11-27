package com.se100.clinic.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import com.se100.clinic.auth.JwtTokenService;
import com.se100.clinic.notification.enums.NotificationChannel;
import com.se100.clinic.notification.enums.NotificationStatus;
import com.se100.clinic.notification.enums.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** The in-app inbox over real HTTP, JWT and PostgreSQL: ownership, filtering and read state. */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class NotificationInboxIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/notifications";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtTokenService jwt;
  @Autowired private NotificationRepository repository;

  private record Account(long userId, String token) {}

  private Account account() {
    long user =
        ScheduleTestData.user(jdbc, "inbox_" + UUID.randomUUID(), "test-password", "PATIENT");
    String subject =
        jdbc.queryForObject("select public_id::text from users where id = ?", String.class, user);
    return new Account(user, jwt.issue(subject).accessToken());
  }

  private UUID save(long userId, NotificationChannel channel, NotificationStatus status) {
    Instant now = Instant.now();
    Notification notification = new Notification();
    notification.setUserId(userId);
    notification.setChannel(channel);
    notification.setType(NotificationType.CARD_EXPIRING);
    notification.setTitle("title");
    notification.setContent("content");
    notification.setDeduplicationKey("inbox:" + UUID.randomUUID());
    notification.setStatus(status);
    notification.setScheduledAt(now);
    notification.setNextAttemptAt(now);
    return repository.save(notification).getPublicId();
  }

  private UUID inApp(long userId) {
    return save(userId, NotificationChannel.IN_APP, NotificationStatus.SENT);
  }

  private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return restTemplate.exchange(
        path, method, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
  }

  private List<String> ids(ResponseEntity<Map<String, Object>> response) {
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    return ((List<?>) response.getBody().get("data"))
        .stream().map(item -> (String) ((Map<?, ?>) item).get("publicId")).toList();
  }

  private int unreadCount(Account account) {
    var response = call(HttpMethod.GET, BASE + "/unread-count", account.token());
    return (Integer) ((Map<?, ?>) response.getBody().get("data")).get("unreadCount");
  }

  @Test
  void listShowsOnlyOwnDeliveredInAppNotifications_newestFirst() {
    Account me = account();
    Account other = account();
    UUID older = inApp(me.userId());
    UUID newer = inApp(me.userId());
    jdbc.update(
        "update notifications set created_at = now() - interval '1 day' where public_id = ?",
        older);
    save(me.userId(), NotificationChannel.EMAIL, NotificationStatus.SENT);
    save(me.userId(), NotificationChannel.IN_APP, NotificationStatus.CANCELLED);
    inApp(other.userId());

    assertThat(ids(call(HttpMethod.GET, BASE, me.token())))
        .containsExactly(newer.toString(), older.toString());
    assertThat(unreadCount(me)).isEqualTo(2);
  }

  @Test
  void markReadRemovesNotificationFromUnreadAndKeepsFirstReadTime() {
    Account me = account();
    UUID read = inApp(me.userId());
    UUID unread = inApp(me.userId());

    var first = call(HttpMethod.POST, BASE + "/" + read + "/read", me.token());
    var second = call(HttpMethod.POST, BASE + "/" + read + "/read", me.token());

    assertThat(first.getStatusCode().value()).isEqualTo(200);
    Object readAt = ((Map<?, ?>) first.getBody().get("data")).get("readAt");
    assertThat(readAt).isNotNull();
    // PostgreSQL keeps microseconds, so compare the stored value with a later read of it.
    var third = call(HttpMethod.POST, BASE + "/" + read + "/read", me.token());
    assertThat(((Map<?, ?>) third.getBody().get("data")).get("readAt"))
        .isEqualTo(((Map<?, ?>) second.getBody().get("data")).get("readAt"));
    assertThat(ids(call(HttpMethod.GET, BASE + "?unreadOnly=true", me.token())))
        .containsExactly(unread.toString());
    assertThat(unreadCount(me)).isEqualTo(1);
  }

  @Test
  void anotherUsersNotificationLooksMissingAndStaysUnread() {
    Account me = account();
    Account other = account();
    UUID theirs = inApp(other.userId());

    var response = call(HttpMethod.POST, BASE + "/" + theirs + "/read", me.token());

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody().get("code")).isEqualTo("NOTIFICATION_NOT_FOUND");
    assertThat(unreadCount(other)).isEqualTo(1);
  }

  @Test
  void readAllMarksOnlyOwnUnreadNotifications() {
    Account me = account();
    Account other = account();
    inApp(me.userId());
    inApp(me.userId());
    inApp(other.userId());

    var response = call(HttpMethod.POST, BASE + "/read-all", me.token());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(((Map<?, ?>) response.getBody().get("data")).get("updated")).isEqualTo(2);
    assertThat(unreadCount(me)).isZero();
    assertThat(unreadCount(other)).isEqualTo(1);
  }

  @Test
  void everyEndpointNeedsLogin() {
    assertThat(call(HttpMethod.GET, BASE, null).getStatusCode().value()).isEqualTo(401);
    assertThat(call(HttpMethod.GET, BASE + "/unread-count", null).getStatusCode().value())
        .isEqualTo(401);
    assertThat(call(HttpMethod.POST, BASE + "/read-all", null).getStatusCode().value())
        .isEqualTo(401);
  }
}
