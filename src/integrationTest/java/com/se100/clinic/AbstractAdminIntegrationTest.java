package com.se100.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for integration tests of endpoints that need a logged-in ADMIN.
 *
 * <p>Role checks ({@code @AdminOnly}, {@code isAuthenticated()}) run with the real method-security
 * proxy, so an anonymous request gets 403. Before each test this class seeds an ADMIN account, logs
 * in through {@code /auth/login} with JWT enabled, and makes {@link #restTemplate} send the Bearer
 * token on every request that does not set its own {@code Authorization} header. The JWT settings
 * match {@code ScheduleJwtIntegrationTest}, so both share one Spring context.
 */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
public abstract class AbstractAdminIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Password123!";

  // The TestRestTemplate bean is shared by every test of a cached context, so the interceptor reads
  // the token from here and is registered only once.
  private static final AtomicReference<String> TOKEN = new AtomicReference<>();
  private static final ClientHttpRequestInterceptor BEARER =
      (request, body, execution) -> {
        String token = TOKEN.get();
        if (token != null && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
          request.getHeaders().setBearerAuth(token);
        }
        return execution.execute(request, body);
      };

  @Autowired private JdbcTemplate jdbc;

  // The template bean (and so the interceptor) is shared with other test classes of the same
  // cached context; never leave a token behind for them.
  @AfterEach
  void clearToken() {
    TOKEN.set(null);
  }

  @BeforeEach
  void loginAsAdmin() {
    var interceptors = restTemplate.getRestTemplate().getInterceptors();
    if (!interceptors.contains(BEARER)) {
      interceptors.add(BEARER);
    }
    // Log in without a stale token from the previous test.
    TOKEN.set(null);
    String username = "admin_" + UUID.randomUUID().toString().substring(0, 8);
    ScheduleTestData.user(jdbc, username, PASSWORD, "ADMIN");
    var response =
        restTemplate.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            new org.springframework.http.HttpEntity<>(
                Map.of("username", username, "password", PASSWORD)),
            new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    TOKEN.set((String) ((Map<?, ?>) response.getBody().get("data")).get("accessToken"));
  }
}
