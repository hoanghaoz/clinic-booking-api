package com.se100.clinic.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.FieldViolation;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  private static final String SECRET = "test-only-random-key-0123456789-abcdef";

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;

  private JwtTokenService jwtTokenService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    jwtTokenService = new JwtTokenService(SECRET, "clinic-booking-api", "clinic-booking-api", 900);
    authService = new AuthService(userRepository, refreshTokenRepository, jwtTokenService, 604800);
  }

  @Test
  void register_patientAndCompany_hashesPasswordNormalizesEmailAndSavesActiveUser() {
    when(userRepository.existsByEmailIgnoreCase("patient@example.com")).thenReturn(false);
    when(userRepository.existsByUsernameIgnoreCase("patient_01")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthDtos.RegisterResponse response =
        authService.register(
            new AuthDtos.RegisterRequest(
                "  patient_01 ", "  Patient@Example.com ", "Password123!", UserRole.PATIENT));

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(savedUser.capture());
    User persisted = savedUser.getValue();

    assertThat(persisted.getUsername()).isEqualTo("patient_01");
    assertThat(persisted.getEmail()).isEqualTo("patient@example.com");
    assertThat(persisted.getRole()).isEqualTo(UserRole.PATIENT);
    assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(persisted.getPasswordHashed()).isNotEqualTo("Password123!");
    assertThat(new BCryptPasswordEncoder().matches("Password123!", persisted.getPasswordHashed()))
        .isTrue();

    assertThat(response.email()).isEqualTo("patient@example.com");
    assertThat(response.role()).isEqualTo(UserRole.PATIENT);
    assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void register_companyWithNullUsername_defaultsUsernameToEmail() {
    when(userRepository.existsByEmailIgnoreCase("hr@company.vn")).thenReturn(false);
    when(userRepository.existsByUsernameIgnoreCase("hr@company.vn")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthDtos.RegisterResponse response =
        authService.register(
            new AuthDtos.RegisterRequest(
                null, "HR@company.vn", "CompanyPass123", UserRole.COMPANY));

    assertThat(response.username()).isEqualTo("hr@company.vn");
    assertThat(response.email()).isEqualTo("hr@company.vn");
    assertThat(response.role()).isEqualTo(UserRole.COMPANY);
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"DOCTOR", "MEDICAL_STAFF", "ADMIN"})
  void register_disallowedRoles_throwsValidationError(UserRole disallowedRole) {
    assertThatThrownBy(
            () ->
                authService.register(
                    new AuthDtos.RegisterRequest(
                        "staff01", "staff01@example.com", "Password123!", disallowedRole)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR);
              assertThat(ex.getViolations())
                  .extracting(FieldViolation::field)
                  .containsExactly("role");
            });

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void register_duplicateEmail_throws409EmailExists() {
    when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.register(
                    new AuthDtos.RegisterRequest(
                        "dupuser", "dup@example.com", "Password123!", UserRole.PATIENT)))
        .isInstanceOfSatisfying(
            ConflictException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_EXISTS));

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void register_dbUniqueViolationOnEmail_translatesTo409EmailExists() {
    when(userRepository.existsByEmailIgnoreCase("race@example.com")).thenReturn(false);
    when(userRepository.existsByUsernameIgnoreCase("race@example.com")).thenReturn(false);
    SQLException sqlEx = new SQLException("duplicate key", "23505");
    ConstraintViolationException cve =
        new ConstraintViolationException("duplicate email", sqlEx, "users_email_key");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate", cve));

    assertThatThrownBy(
            () ->
                authService.register(
                    new AuthDtos.RegisterRequest(
                        null, "race@example.com", "Password123!", UserRole.PATIENT)))
        .isInstanceOfSatisfying(
            ConflictException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_EXISTS));
  }

  @Test
  void login_validCredentials_issuesAccessTokenAndStoresHashedRefreshToken() {
    User user =
        activeUser(10L, "patient01", "patient01@example.com", "Password123!", UserRole.PATIENT);
    when(userRepository.findByUsernameOrEmail("patient01@example.com")).thenReturn(List.of(user));

    AuthDtos.IssuedSession session =
        authService.login(new AuthDtos.LoginRequest("patient01@example.com", "Password123!"));

    assertThat(session.tokenResponse().accessToken()).isNotBlank();
    assertThat(session.tokenResponse().tokenType()).isEqualTo("Bearer");
    assertThat(session.tokenResponse().expiresIn()).isEqualTo(900L);
    assertThat(session.refreshToken()).isNotBlank();
    assertThat(session.refreshExpiresIn()).isEqualTo(604800L);

    ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).saveAndFlush(savedToken.capture());
    RefreshToken persisted = savedToken.getValue();
    assertThat(persisted.getUser()).isSameAs(user);
    assertThat(persisted.getTokenHash()).isNotEqualTo(session.refreshToken());
    assertThat(persisted.getTokenHash())
        .isEqualTo(AuthService.hashRefreshToken(session.refreshToken()));
    assertThat(persisted.getRevokedAt()).isNull();
    assertThat(persisted.getExpiresAt()).isAfter(Instant.now());
  }

  @Test
  void login_wrongPasswordOrUnknownOrDisabled_throws401GenericInvalidCredentials() {
    User user =
        activeUser(10L, "patient01", "patient01@example.com", "Password123!", UserRole.PATIENT);
    when(userRepository.findByUsernameOrEmail("patient01")).thenReturn(List.of(user));
    when(userRepository.findByUsernameOrEmail("unknown")).thenReturn(List.of());

    for (AuthDtos.LoginRequest badRequest :
        List.of(
            new AuthDtos.LoginRequest("patient01", "WrongPassword"),
            new AuthDtos.LoginRequest("unknown", "Password123!"),
            new AuthDtos.LoginRequest("patient01", "ậ".repeat(40)))) {
      assertThatThrownBy(() -> authService.login(badRequest))
          .isInstanceOfSatisfying(
              BusinessException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
                assertThat(ex.getMessage()).isEqualTo("Thông tin đăng nhập không hợp lệ");
              });
    }

    user.setStatus(UserStatus.DISABLED);
    assertThatThrownBy(
            () -> authService.login(new AuthDtos.LoginRequest("patient01", "Password123!")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
  }

  @Test
  void refresh_validToken_rotatesOldTokenAndIssuesNewPair() {
    User user =
        activeUser(15L, "patient01", "patient01@example.com", "Password123!", UserRole.PATIENT);
    String rawOldToken = "old-raw-refresh-token";
    RefreshToken stored = new RefreshToken();
    stored.setUser(user);
    stored.setTokenHash(AuthService.hashRefreshToken(rawOldToken));
    stored.setExpiresAt(Instant.now().plusSeconds(3600));

    when(refreshTokenRepository.findByTokenHashForUpdate(stored.getTokenHash()))
        .thenReturn(Optional.of(stored));

    AuthDtos.IssuedSession newSession = authService.refresh(rawOldToken);

    assertThat(stored.getRevokedAt()).isNotNull();
    assertThat(newSession.refreshToken()).isNotBlank().isNotEqualTo(rawOldToken);
    assertThat(newSession.tokenResponse().accessToken()).isNotBlank();
    verify(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
  }

  @Test
  void refresh_whenRevokedTokenIsReused_revokesAllActiveUserSessionsAndThrows401() {
    User user =
        activeUser(22L, "patient01", "patient01@example.com", "Password123!", UserRole.PATIENT);
    String reusedRawToken = "already-rotated-token";
    RefreshToken revokedToken = new RefreshToken();
    revokedToken.setUser(user);
    revokedToken.setTokenHash(AuthService.hashRefreshToken(reusedRawToken));
    revokedToken.setExpiresAt(Instant.now().plusSeconds(3600));
    revokedToken.setRevokedAt(Instant.now().minusSeconds(60));

    when(refreshTokenRepository.findByTokenHashForUpdate(revokedToken.getTokenHash()))
        .thenReturn(Optional.of(revokedToken));

    assertThatThrownBy(() -> authService.refresh(reusedRawToken))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

    verify(refreshTokenRepository).revokeAllActiveByUserId(eq(22L), any(Instant.class));
    verify(refreshTokenRepository, never()).saveAndFlush(any());
  }

  @Test
  void logout_revokesActiveToken_andIgnoresMissingToken() {
    User user =
        activeUser(15L, "patient01", "patient01@example.com", "Password123!", UserRole.PATIENT);
    String rawToken = "active-refresh-token";
    RefreshToken stored = new RefreshToken();
    stored.setUser(user);
    stored.setTokenHash(AuthService.hashRefreshToken(rawToken));
    stored.setExpiresAt(Instant.now().plusSeconds(3600));

    when(refreshTokenRepository.findByTokenHashForUpdate(stored.getTokenHash()))
        .thenReturn(Optional.of(stored));

    authService.logout(rawToken);
    assertThat(stored.getRevokedAt()).isNotNull();

    authService.logout(null);
    authService.logout("   ");
  }

  private static User activeUser(
      Long id, String username, String email, String rawPassword, UserRole role) {
    User user = new User();
    user.setId(id);
    user.setPublicId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHashed(new BCryptPasswordEncoder().encode(rawPassword));
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE);
    return user;
  }
}
