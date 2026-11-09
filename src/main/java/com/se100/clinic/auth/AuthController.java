package com.se100.clinic.auth;

import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Xác thực", description = "Đăng ký, đăng nhập, làm mới token và đăng xuất")
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
class AuthController {
  static final String DEFAULT_REFRESH_COOKIE_NAME = "refresh_token";
  static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

  private final AuthService service;
  private final String refreshCookieName;
  private final boolean cookieSecure;
  private final String cookieSameSite;

  @Autowired
  AuthController(
      AuthService service,
      @Value("${app.security.jwt.refresh-cookie-name:" + DEFAULT_REFRESH_COOKIE_NAME + "}")
          String refreshCookieName,
      @Value("${app.security.jwt.cookie-secure:false}") boolean cookieSecure,
      @Value("${app.security.jwt.cookie-same-site:Lax}") String cookieSameSite) {
    this.service = service;
    this.refreshCookieName = refreshCookieName;
    this.cookieSecure = cookieSecure;
    this.cookieSameSite = cookieSameSite;
  }

  AuthController(AuthService service) {
    this(service, DEFAULT_REFRESH_COOKIE_NAME, false, "Lax");
  }

  @PostMapping("/register")
  @Operation(
      summary = "Đăng ký tài khoản (dành cho bệnh nhân và doanh nghiệp)",
      description =
          "Bác sĩ và nhân viên y tế được Admin cấp tài khoản, không tự đăng ký qua endpoint này.")
  @ApiResponse(
      responseCode = "201",
      description = "Đã tạo tài khoản; header Location trỏ tới tài nguyên mới")
  @ApiResponse(
      responseCode = "409",
      description = "EMAIL_EXISTS / USERNAME_EXISTS — email hoặc tên đăng nhập đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<AuthDtos.RegisterResponse>> register(
      @Valid @RequestBody AuthDtos.RegisterRequest request) {
    AuthDtos.RegisterResponse created = service.register(request);
    return ResponseEntity.created(URI.create("/api/v1/auth/users/" + created.publicId()))
        .body(ApiResult.of(created));
  }

  @PostMapping("/login")
  @Operation(summary = "Đăng nhập cấp access token và refresh token (qua cookie httpOnly)")
  @ApiResponse(
      responseCode = "401",
      description = "INVALID_CREDENTIALS — thông tin đăng nhập không hợp lệ",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<AuthDtos.TokenResponse>> login(
      @Valid @RequestBody AuthDtos.LoginRequest request) {
    AuthDtos.IssuedSession session = service.login(request);
    return respondWithSession(session);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Làm mới access token và xoay vòng refresh token (rotation)",
      description =
          "Đọc refresh token từ cookie httpOnly, thu hồi token cũ và cấp cặp mới."
              + " Nếu token đã thu hồi bị dùng lại, toàn bộ session của user sẽ bị thu hồi.")
  @ApiResponse(
      responseCode = "401",
      description =
          "INVALID_REFRESH_TOKEN — refresh token thiếu, hết hạn, đã thu hồi hoặc không hợp lệ",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<AuthDtos.TokenResponse>> refresh(HttpServletRequest request) {
    AuthDtos.IssuedSession session = service.refresh(extractRefreshToken(request));
    return respondWithSession(session);
  }

  @PostMapping("/logout")
  @Operation(summary = "Đăng xuất, thu hồi refresh token hiện tại và xóa cookie")
  @ApiResponse(responseCode = "204", description = "Thành công, KHÔNG có body")
  ResponseEntity<Void> logout(HttpServletRequest request) {
    service.logout(extractRefreshToken(request));
    ResponseCookie cleared = buildRefreshCookie("", Duration.ZERO);
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .header(HttpHeaders.SET_COOKIE, cleared.toString())
        .build();
  }

  private ResponseEntity<ApiResult<AuthDtos.TokenResponse>> respondWithSession(
      AuthDtos.IssuedSession session) {
    ResponseCookie cookie =
        buildRefreshCookie(session.refreshToken(), Duration.ofSeconds(session.refreshExpiresIn()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiResult.of(session.tokenResponse()));
  }

  private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(refreshCookieName, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }

  private @Nullable String extractRefreshToken(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, refreshCookieName);
    return cookie == null ? null : cookie.getValue();
  }
}
