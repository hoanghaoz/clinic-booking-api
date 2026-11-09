package com.se100.clinic.facility;

import com.se100.clinic.facility.enums.FacilityStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Room and clinic availability exposed to the schedule module. */
@Service
@RequiredArgsConstructor
public class FacilitySchedulingService {
  private final EntityManager entityManager;

  /** Serializes a new assignment with an administrator closing its clinic or room. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForAssignment(Long roomId) {
    List<Long> clinicIds =
        entityManager
            .createQuery("select r.clinic.id from Room r where r.id = :roomId", Long.class)
            .setParameter("roomId", roomId)
            .getResultList();
    if (!clinicIds.isEmpty()) {
      entityManager.find(Clinic.class, clinicIds.getFirst(), LockModeType.PESSIMISTIC_READ);
      entityManager.find(Room.class, roomId, LockModeType.PESSIMISTIC_READ);
    }
  }

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

  @Transactional(readOnly = true)
  public List<Long> roomIds(Long clinicId) {
    return entityManager
        .createQuery("select r.id from Room r where r.clinic.id = :clinicId", Long.class)
        .setParameter("clinicId", clinicId)
        .getResultList();
  }

  @Transactional(readOnly = true)
  public List<Long> openRoomIds(Long clinicId) {
    return entityManager
        .createQuery(
            "select r.id from Room r join r.clinic c "
                + "where r.status = :status and c.status = :status "
                + "and (:clinicId is null or c.id = :clinicId)",
            Long.class)
        .setParameter("status", FacilityStatus.OPENING)
        .setParameter("clinicId", clinicId)
        .getResultList();
  }

  public record RoomAssignment(Long clinicId, boolean roomOpen, boolean clinicOpen) {}
}
