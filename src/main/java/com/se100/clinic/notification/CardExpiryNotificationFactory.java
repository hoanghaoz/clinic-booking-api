package com.se100.clinic.notification;

import com.se100.clinic.notification.dtos.CardExpiryReminderMessage;
import com.se100.clinic.notification.dtos.NotificationRequest;
import com.se100.clinic.notification.enums.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the channel-independent notification for a patient card that is about to expire. */
@Component
@RequiredArgsConstructor
public class CardExpiryNotificationFactory {

  private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final Clock clock;

  public NotificationRequest create(CardExpiryReminderMessage message) {
    // The calendar day in the app time zone, so the text and the dedup key agree on the date.
    LocalDate expiryDate = message.expiresAt().atZone(clock.getZone()).toLocalDate();

    String title = "Thẻ bệnh nhân sắp hết hạn";
    String content =
        "Thẻ bệnh nhân của bạn sẽ hết hạn vào ngày "
            + DISPLAY_DATE.format(expiryDate)
            + ". Vui lòng gia hạn thông tin thẻ trước thời điểm này để tiếp tục sử dụng dịch vụ.";

    // One reminder per patient per expiry date; a renewed card has a new date, so it reminds again.
    String dedupKey = message.patientId() + ":" + expiryDate;

    // No channel preference: the reminder goes out by email and in-app.
    return NotificationRequest.forAllChannels(
        NotificationType.CARD_EXPIRING, message.patientId(), title, content, dedupKey);
  }
}
