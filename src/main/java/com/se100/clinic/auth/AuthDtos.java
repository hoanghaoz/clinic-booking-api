package com.se100.clinic.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.auth.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  @Schema(requiredProperties = {"email", "password"})
  public record RegisterRequest(
      @Schema(description = "Tên đăng nhập (tùy chọn; mặc định lấy theo email nếu bỏ trống)")
          @Size(min = 3, max = 100, message = "Tên đăng nhập phải từ 3 đến 100 ký tự")
          @Pattern(regexp = ".*\\S.*", message = "Tên đăng nhập không được để trống")
          String username,
      @Schema(description = "Địa chỉ email đăng ký", example = "patient@example.com")
          @NotBlank(message = "Email không được để trống")
          @Email(message = "Email không đúng định dạng")
          @Size(max = 254, message = "Email tối đa 254 ký tự")
          String email,
      @Schema(description = "Mật khẩu (8–72 ký tự)", example = "Password123!")
          @NotBlank(message = "Mật khẩu không được để trống")
          @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
          String password,
      @Schema(
              description =
                  "Vai trò tự đăng ký: PATIENT (mặc định) hoặc COMPANY."
                      + " Bác sĩ và nhân viên y tế do Admin cấp tài khoản.",
              example = "PATIENT")
          @SelfRegistrableRole
          UserRole role) {}

  @Schema(requiredProperties = {"publicId", "username", "email", "role", "status", "createdAt"})
  public record RegisterResponse(
      UUID publicId,
      String username,
      String email,
      UserRole role,
      UserStatus status,
      Instant createdAt) {
    static RegisterResponse from(User user) {
      return new RegisterResponse(
          user.getPublicId(),
          user.getUsername(),
          user.getEmail(),
          user.getRole(),
          user.getStatus(),
          user.getCreatedAt());
    }
  }

  @Schema(requiredProperties = {"username", "password"})
  public record LoginRequest(
      @Schema(description = "Tên đăng nhập hoặc email", example = "patient@example.com")
          @JsonAlias("email")
          @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
          @Size(max = 254, message = "Tên đăng nhập hoặc email tối đa 254 ký tự")
          String username,
      @Schema(description = "Mật khẩu")
          @NotBlank(message = "Mật khẩu không được để trống")
          @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
          String password) {}

  @Schema(requiredProperties = {"accessToken", "tokenType", "expiresIn"})
  public record TokenResponse(
      String accessToken,
      String tokenType,
      long expiresIn,
      String username,
      String email,
      UserRole role) {
    public TokenResponse(String accessToken, String tokenType, long expiresIn) {
      this(accessToken, tokenType, expiresIn, null, null, null);
    }
  }

  public record IssuedSession(
      TokenResponse tokenResponse, String refreshToken, long refreshExpiresIn) {}

  public record AccountIdentity(Long userId, String username, String role) {}
}
