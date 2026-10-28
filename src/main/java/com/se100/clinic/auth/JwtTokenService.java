package com.se100.clinic.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Short-lived access tokens for existing accounts; secrets are supplied by the deployment. */
@Service
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
public class JwtTokenService {
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final String issuer;
  private final String audience;
  private final long lifetimeSeconds;

  public JwtTokenService(
      @Value("${app.security.jwt.secret:}") String secret,
      @Value("${app.security.jwt.issuer:clinic-booking-api}") String issuer,
      @Value("${app.security.jwt.audience:clinic-booking-api}") String audience,
      @Value("${app.security.jwt.lifetime-seconds:1800}") long lifetimeSeconds) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32 || lifetimeSeconds < 60 || lifetimeSeconds > 3600) {
      throw new IllegalArgumentException(
          "JWT requires a secret of at least 32 bytes and lifetime 60–3600 seconds");
    }
    SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
    this.issuer = issuer;
    this.audience = audience;
    this.lifetimeSeconds = lifetimeSeconds;
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt ->
                jwt.getAudience().contains(audience) && jwt.getExpiresAt() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    this.decoder = jwtDecoder;
  }

  public JwtDecoder decoder() {
    return decoder;
  }

  public AuthDtos.TokenResponse issue(String subject) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(lifetimeSeconds))
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AuthDtos.TokenResponse(token, "Bearer", lifetimeSeconds);
  }
}
