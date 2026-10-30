package com.se100.clinic.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank @Size(max = 254) String username, @NotBlank @Size(max = 72) String password) {}

  public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

  public record AccountIdentity(Long userId, String username, String role) {}
}
