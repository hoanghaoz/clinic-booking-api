package com.se100.clinic.auth;

import com.se100.clinic.shared.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
class AuthController {
  private final AuthService service;

  @PostMapping("/login")
  ResponseEntity<ApiResult<AuthDtos.TokenResponse>> login(
      @Valid @RequestBody AuthDtos.LoginRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .body(ApiResult.of(service.login(request)));
  }
}
