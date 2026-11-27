package com.se100.clinic.notification;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.notification.NotificationDtos.MarkAllReadResponse;
import com.se100.clinic.notification.NotificationDtos.NotificationResponse;
import com.se100.clinic.notification.NotificationDtos.UnreadCountResponse;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own in-app notifications. Open to every role; ownership scopes the data. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "Thông báo in-app của người dùng đang đăng nhập")
class NotificationController {

  private final NotificationInboxService inbox;
  private final AuthenticatedUserService users;

  NotificationController(NotificationInboxService inbox, AuthenticatedUserService users) {
    this.inbox = inbox;
    this.users = users;
  }

  @GetMapping
  @Operation(
      summary = "Danh sách thông báo của tôi",
      description = "Sắp xếp được theo: createdAt. Mặc định: createdAt giảm dần (mới nhất trước).")
  ApiResult<List<NotificationResponse>> list(
      Authentication authentication,
      @Parameter(description = "true thì chỉ lấy thông báo chưa đọc")
          @RequestParam(defaultValue = "false")
          boolean unreadOnly,
      @ParameterObject PageParams pageParams) {
    var pageable =
        pageParams.toPageable(
            NotificationInboxService.SORTABLE_FIELDS, NotificationInboxService.DEFAULT_SORT);
    return ApiResult.of(inbox.list(userId(authentication), unreadOnly, pageable));
  }

  @GetMapping("/unread-count")
  @Operation(summary = "Số thông báo chưa đọc của tôi")
  ApiResult<UnreadCountResponse> unreadCount(Authentication authentication) {
    return ApiResult.of(inbox.countUnread(userId(authentication)));
  }

  @PostMapping("/{publicId}/read")
  @Operation(
      summary = "Đánh dấu một thông báo là đã đọc",
      description = "Gọi lại nhiều lần vẫn giữ thời điểm đọc đầu tiên.")
  @ApiResponse(
      responseCode = "404",
      description = "NOTIFICATION_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<NotificationResponse> markRead(
      Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(inbox.markRead(userId(authentication), publicId));
  }

  @PostMapping("/read-all")
  @Operation(summary = "Đánh dấu mọi thông báo của tôi là đã đọc")
  ApiResult<MarkAllReadResponse> markAllRead(Authentication authentication) {
    return ApiResult.of(inbox.markAllRead(userId(authentication)));
  }

  private Long userId(Authentication authentication) {
    if (authentication == null) {
      throw authRequired();
    }
    return users
        .userIdForPrincipal(authentication.getName())
        .orElseThrow(NotificationController::authRequired);
  }

  private static BusinessException authRequired() {
    return new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập");
  }
}
