package com.se100.clinic.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void register_returns201_withLocation_andDataEnvelope() throws Exception {
    UUID publicId = UUID.randomUUID();
    when(authService.register(any()))
        .thenReturn(
            new AuthDtos.RegisterResponse(
                publicId,
                "patient01",
                "patient01@example.com",
                UserRole.PATIENT,
                UserStatus.ACTIVE,
                Instant.EPOCH));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "patient01",
                      "email": "patient01@example.com",
                      "password": "Password123!",
                      "role": "PATIENT"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/auth/users/" + publicId))
        .andExpect(jsonPath("$.data.publicId").value(publicId.toString()))
        .andExpect(jsonPath("$.data.email").value("patient01@example.com"))
        .andExpect(jsonPath("$.data.role").value("PATIENT"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void register_withDoctorRoleOrInvalidFields_returns400ValidationError_andNeverCallsService()
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "not-an-email",
                      "password": "short",
                      "role": "DOCTOR"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(3));

    verifyNoInteractions(authService);
  }

  @Test
  void register_whenDuplicateEmail_returns409EmailExists() throws Exception {
    when(authService.register(any()))
        .thenThrow(new ConflictException(AuthErrorCode.EMAIL_EXISTS, "Email đã được đăng ký"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "dup@example.com",
                      "password": "Password123!",
                      "role": "COMPANY"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
  }

  @Test
  void login_returns200_withAccessTokenAndHttpOnlyCookie() throws Exception {
    when(authService.login(any()))
        .thenReturn(
            new AuthDtos.IssuedSession(
                new AuthDtos.TokenResponse("jwt-access-token", "Bearer", 900),
                "raw-refresh-token-value",
                604800));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"patient@example.com\",\"password\":\"Password123!\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    Matchers.allOf(
                        Matchers.containsString("refresh_token=raw-refresh-token-value"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("Path=/api/v1/auth"),
                        Matchers.containsString("Max-Age=604800"))))
        .andExpect(jsonPath("$.data.accessToken").value("jwt-access-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  void refresh_readsRefreshCookie_andReturnsRotatedPair() throws Exception {
    when(authService.refresh("incoming-refresh-token"))
        .thenReturn(
            new AuthDtos.IssuedSession(
                new AuthDtos.TokenResponse("new-jwt-access", "Bearer", 900),
                "rotated-refresh-token",
                604800));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie("refresh_token", "incoming-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    Matchers.allOf(
                        Matchers.containsString("refresh_token=rotated-refresh-token"),
                        Matchers.containsString("HttpOnly"))))
        .andExpect(jsonPath("$.data.accessToken").value("new-jwt-access"));

    verify(authService).refresh("incoming-refresh-token");
  }

  @Test
  void refresh_whenTokenInvalidOrReused_returns401InvalidRefreshToken() throws Exception {
    when(authService.refresh(any()))
        .thenThrow(
            new BusinessException(
                AuthErrorCode.INVALID_REFRESH_TOKEN, "Refresh token không hợp lệ hoặc đã hết hạn"));

    mockMvc
        .perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logout_revokesToken_clearsCookie_andReturns204WithEmptyBody() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", "token-to-revoke")))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    Matchers.allOf(
                        Matchers.containsString("refresh_token=;"),
                        Matchers.containsString("Max-Age=0"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("Path=/api/v1/auth"))));

    verify(authService).logout("token-to-revoke");
  }
}
