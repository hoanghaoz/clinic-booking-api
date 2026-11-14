package com.se100.clinic.notification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  boolean existsByDeduplicationKey(String deduplicationKey);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);
}
