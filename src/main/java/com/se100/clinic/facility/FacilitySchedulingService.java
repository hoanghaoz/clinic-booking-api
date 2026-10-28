package com.se100.clinic.facility;

import com.se100.clinic.facility.enums.FacilityStatus;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only room and clinic facts exposed to the schedule module. */
@Service
@RequiredArgsConstructor
public class FacilitySchedulingService {
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public Optional<RoomAssignment> roomAssignment(Long roomId) {
    Room room = entityManager.find(Room.class, roomId);
    if (room == null) {
      return Optional.empty();
    }
    Clinic clinic = room.getClinic();
    return Optional.of(
        new RoomAssignment(
            clinic.getId(),
            room.getStatus() == FacilityStatus.OPENING,
            clinic.getStatus() == FacilityStatus.OPENING));
  }

  public record RoomAssignment(Long clinicId, boolean roomOpen, boolean clinicOpen) {}
}
