package com.se100.clinic.auth;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
public class AuthService {
  static final long DEFAULT_REFRESH_LIFETIME_SECONDS = 7L * 24 * 3600;
  private static final long MIN_REFRESH_LIFETIME_SECONDS = 60L;
  private static final long MAX_REFRESH_LIFETIME_SECONDS = 30L * 24 * 3600;
  private static final int REFRESH_TOKEN_BYTES = 32;
  private static final int BCRYPT_MAX_BYTES = 72;

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenService tokens;
  private final long refreshLifetimeSeconds;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom secureRandom = new SecureRandom();
  private final String dummyHash = passwords.encode("unused-account-timing-padding");

  @Autowired
  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtTokenService tokens,
      @Value("${app.security.jwt.refresh-lifetime-seconds:604800}") long refreshLifetimeSeconds) {
    if (refreshLifetimeSeconds < MIN_REFRESH_LIFETIME_SECONDS
        || refreshLifetimeSeconds > MAX_REFRESH_LIFETIME_SECONDS) {
      throw new IllegalArgumentException(
          "Refresh token lifetime must be between 60 and 2592000 seconds (up to 30 days)");
    }
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.tokens = tokens;
    this.refreshLifetimeSeconds = refreshLifetimeSeconds;
  }

  AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtTokenService tokens) {
    this(userRepository, refreshTokenRepository, tokens, DEFAULT_REFRESH_LIFETIME_SECONDS);
  }

  @Transactional
  public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
    List<FieldViolation> violations = new ArrayList<>();

    String normalizedEmail =
        request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
    if (normalizedEmail.isEmpty() || !isValidEmail(normalizedEmail)) {
      violations.add(new FieldViolation("email", "Email không đúng định dạng"));
    } else if (normalizedEmail.length() > 254) {
      violations.add(new FieldViolation("email", "Email tối đa 254 ký tự"));
    }

    String rawPassword = request.password();
    if (rawPassword == null || rawPassword.isBlank()) {
      violations.add(new FieldViolation("password", "Mật khẩu không được để trống"));
    } else if (rawPassword.length() < 8 || rawPassword.length() > BCRYPT_MAX_BYTES) {
      violations.add(new FieldViolation("password", "Mật khẩu phải từ 8 đến 72 ký tự"));
    } else if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      violations.add(new FieldViolation("password", "Mật khẩu không được vượt quá 72 byte UTF-8"));
    }

    UserRole resolvedRole = request.role() == null ? UserRole.PATIENT : request.role();
    if (resolvedRole != UserRole.PATIENT && resolvedRole != UserRole.COMPANY) {
      violations.add(
          new FieldViolation(
              "role", "Chỉ bệnh nhân (PATIENT) và doanh nghiệp (COMPANY) được phép tự đăng ký"));
    }

    String resolvedUsername;
    if (request.username() != null) {
      resolvedUsername = request.username().trim();
      if (resolvedUsername.length() < 3 || resolvedUsername.length() > 100) {
        violations.add(new FieldViolation("username", "Tên đăng nhập phải từ 3 đến 100 ký tự"));
      }
    } else {
      resolvedUsername = normalizedEmail;
      if (resolvedUsername.length() > 100) {
        violations.add(
            new FieldViolation(
                "username",
                "Email dài hơn 100 ký tự, vui lòng nhập tên đăng nhập riêng tối đa 100 ký tự"));
      }
    }

    if (!violations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR, "Dữ liệu gửi lên không hợp lệ", violations);
    }

    if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
      throw emailExists(normalizedEmail);
    }
    if (userRepository.existsByUsernameIgnoreCase(resolvedUsername)) {
      if (request.username() == null || resolvedUsername.equalsIgnoreCase(normalizedEmail)) {
        throw emailExists(normalizedEmail);
      }
      throw usernameExists(resolvedUsername);
    }

    User user = new User();
    user.setPublicId(UUID.randomUUID());
    user.setUsername(resolvedUsername);
    user.setEmail(normalizedEmail);
    user.setPasswordHashed(passwords.encode(rawPassword));
    user.setRole(resolvedRole);
    user.setStatus(UserStatus.ACTIVE);

    try {
      return AuthDtos.RegisterResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException e) {
      if (DbErrors.isUniqueViolation(e)) {
        String constraint = DbErrors.constraintName(e);
        if (constraint != null
            && constraint.toLowerCase(Locale.ROOT).contains("username")
            && request.username() != null
            && !resolvedUsername.equalsIgnoreCase(normalizedEmail)) {
          throw usernameExists(resolvedUsername);
        }
        throw emailExists(normalizedEmail);
      }
      throw e;
    }
  }

  @Transactional
  public AuthDtos.IssuedSession login(AuthDtos.LoginRequest request) {
    if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      passwords.matches("oversized-password-padding", dummyHash);
      throw invalidCredentials();
    }
    String identifier = request.username().trim();
    List<User> matches = userRepository.findByUsernameOrEmail(identifier);
    User user = matches.size() == 1 ? matches.getFirst() : null;
    boolean valid =
        passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHashed());
    if (!valid || user == null || user.getStatus() != UserStatus.ACTIVE) {
      throw invalidCredentials();
    }
    return issueSession(user, Instant.now());
  }

  /**
   * Verifies the refresh token, rotates it, and issues a new token pair. If a previously revoked
   * refresh token is presented again (token reuse), all active refresh tokens of that user are
   * revoked immediately and committed before returning 401.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public AuthDtos.IssuedSession refresh(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw invalidRefreshToken();
    }
    String tokenHash = hashRefreshToken(rawRefreshToken.trim());
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(AuthService::invalidRefreshToken);

    Instant now = Instant.now();
    User user = stored.getUser();

    if (stored.getRevokedAt() != null) {
      refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
      throw invalidRefreshToken();
    }

    if (!stored.getExpiresAt().isAfter(now)) {
      throw invalidRefreshToken();
    }

    if (user.getStatus() != UserStatus.ACTIVE) {
      refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
      throw invalidRefreshToken();
    }

    stored.setRevokedAt(now);
    return issueSession(user, now);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    String tokenHash = hashRefreshToken(rawRefreshToken.trim());
    refreshTokenRepository
        .findByTokenHashForUpdate(tokenHash)
        .ifPresent(
            token -> {
              if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
              }
            });
  }

  private AuthDtos.IssuedSession issueSession(User user, Instant now) {
    AuthDtos.TokenResponse baseToken = tokens.issue(user.getPublicId().toString());
    AuthDtos.TokenResponse accessToken =
        new AuthDtos.TokenResponse(
            baseToken.accessToken(),
            baseToken.tokenType(),
            baseToken.expiresIn(),
            user.getUsername(),
            user.getEmail(),
            user.getRole());
    String rawRefreshToken = generateRawRefreshToken();

    RefreshToken entity = new RefreshToken();
    entity.setUser(user);
    entity.setTokenHash(hashRefreshToken(rawRefreshToken));
    entity.setExpiresAt(now.plusSeconds(refreshLifetimeSeconds));
    refreshTokenRepository.saveAndFlush(entity);

    return new AuthDtos.IssuedSession(accessToken, rawRefreshToken, refreshLifetimeSeconds);
  }

  private String generateRawRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hashRefreshToken(String rawRefreshToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required", e);
    }
  }

  private static boolean isValidEmail(String email) {
    int at = email.indexOf('@');
    return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1;
  }

  private static ConflictException emailExists(String email) {
    return new ConflictException(AuthErrorCode.EMAIL_EXISTS, "Email đã được đăng ký: " + email);
  }

  private static ConflictException usernameExists(String username) {
    return new ConflictException(
        AuthErrorCode.USERNAME_EXISTS, "Tên đăng nhập đã tồn tại: " + username);
  }

  private static BusinessException invalidCredentials() {
    return new BusinessException(
        AuthErrorCode.INVALID_CREDENTIALS, "Thông tin đăng nhập không hợp lệ");
  }

  private static BusinessException invalidRefreshToken() {
    return new BusinessException(
        AuthErrorCode.INVALID_REFRESH_TOKEN, "Refresh token không hợp lệ hoặc đã hết hạn");
  }
}
