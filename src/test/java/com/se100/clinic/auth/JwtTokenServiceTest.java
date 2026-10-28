package com.se100.clinic.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenServiceTest {
  private static final String SECRET = "test-only-random-key-0123456789-abcdef";

  @Test
  void accessTokenRoundTripsWithIssuerAudienceAndExpiry() {
    var service = new JwtTokenService(SECRET, "clinic", "api", 1800);
    String subject = UUID.randomUUID().toString();
    var result = service.issue(subject);
    var decoded = service.decoder().decode(result.accessToken());
    assertThat(decoded.getSubject()).isEqualTo(subject);
    assertThat(decoded.getAudience()).containsExactly("api");
    assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    assertThat(result.tokenType()).isEqualTo("Bearer");
  }

  @Test
  void wrongIssuerAudienceOrSignatureCannotAuthenticate() {
    var service = new JwtTokenService(SECRET, "clinic", "api", 1800);
    for (var other :
        new JwtTokenService[] {
          new JwtTokenService(SECRET, "foreign-issuer", "api", 1800),
          new JwtTokenService(SECRET, "clinic", "foreign-audience", 1800),
          new JwtTokenService("different-test-key-0123456789-abcdef", "clinic", "api", 1800)
        }) {
      assertThatThrownBy(
              () ->
                  service.decoder().decode(other.issue(UUID.randomUUID().toString()).accessToken()))
          .isInstanceOf(JwtException.class);
    }
  }

  @Test
  void shortSecretFailsAtStartup() {
    assertThatThrownBy(() -> new JwtTokenService("short", "clinic", "api", 1800))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
