package com.se100.clinic.schedule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DoctorScheduleSlotRepository extends JpaRepository<DoctorScheduleSlot, Long> {
  List<DoctorScheduleSlot> findAllByScheduleIdOrderByStartAt(Long scheduleId);

  Optional<DoctorScheduleSlot> findByIdAndScheduleId(Long slotId, Long scheduleId);

  @Modifying
  @Query("delete from DoctorScheduleSlot slot where slot.schedule.id = :scheduleId")
  void deleteForSchedule(@Param("scheduleId") Long scheduleId);
}
