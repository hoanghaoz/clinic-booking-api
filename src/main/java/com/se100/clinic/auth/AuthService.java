package com.se100.clinic.auth;

import com.se100.clinic.auth.enums.UserStatus;
import com.se100.clinic.shared.error.BusinessException;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
public class AuthService {
  private final EntityManager entityManager;
  private final JwtTokenService tokens;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final String dummyHash = passwords.encode("unused-account-timing-padding");

  @Transactional(readOnly = true)
  public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BusinessException(
          AuthErrorCode.INVALID_CREDENTIALS, "Thông tin đăng nhập không hợp lệ");
    }
    List<User> matches =
        entityManager
            .createQuery(
                "select u from User u where u.username = :name or u.email = :name", User.class)
            .setParameter("name", request.username())
            .getResultList();
    User user = matches.size() == 1 ? matches.getFirst() : null;
    boolean valid =
        passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHashed());
    if (!valid || user == null || user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(
          AuthErrorCode.INVALID_CREDENTIALS, "Thông tin đăng nhập không hợp lệ");
    }
    return tokens.issue(user.getPublicId().toString());
  }
}
