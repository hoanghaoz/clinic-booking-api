package com.se100.clinic.notification;

import com.se100.clinic.notification.enums.NotificationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  boolean existsByDeduplicationKey(String deduplicationKey);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);

  /**
   * Atomically claims a notification for sending, so only one consumer sends it.
   *
   * <p>A row can be claimed when it is in one of the {@code claimable} states (PENDING, FAILED), or
   * when it is PROCESSING but its lock has expired (the consumer that held it probably died). The
   * claim sets PROCESSING, a new {@code lockedUntil} and counts the attempt.
   *
   * <p>Runs in its own transaction on purpose: it must be committed before the mail is sent, or
   * other consumers would not see the claim.
   *
   * @return 1 if the caller now owns the row and may send; 0 if another consumer holds it or it was
   *     already sent
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE Notification n
      SET n.status = :processing,
          n.lockedUntil = :lockedUntil,
          n.attemptCount = n.attemptCount + 1,
          n.updatedAt = :now
      WHERE n.deduplicationKey = :deduplicationKey
        AND (n.status IN :claimable
             OR (n.status = :processing AND n.lockedUntil < :now))
      """)
  int claimForSending(
      String deduplicationKey,
      Instant now,
      Instant lockedUntil,
      NotificationStatus processing,
      Collection<NotificationStatus> claimable);
}
