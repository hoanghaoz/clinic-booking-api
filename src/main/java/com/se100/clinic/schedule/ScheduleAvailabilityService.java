package com.se100.clinic.schedule;

import com.se100.clinic.appointment.AppointmentSchedulingService;
import com.se100.clinic.appointment.AppointmentSchedulingService.ReservationSnapshot;
import com.se100.clinic.schedule.ScheduleDtos.AvailableSchedule;
import com.se100.clinic.schedule.ScheduleDtos.AvailableWindow;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.schedule.enums.SlotStatus;
import com.se100.clinic.shared.error.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes candidate appointment windows. Booking still rechecks and reserves them atomically. */
@Service
public class ScheduleAvailabilityService {
  private final ScheduleService schedules;
  private final DoctorScheduleRepository scheduleRepository;
  private final DoctorScheduleSlotRepository slotRepository;
  private final AppointmentSchedulingService appointments;

  public ScheduleAvailabilityService(
      ScheduleService schedules,
      DoctorScheduleRepository scheduleRepository,
      DoctorScheduleSlotRepository slotRepository,
      AppointmentSchedulingService appointments) {
    this.schedules = schedules;
    this.scheduleRepository = scheduleRepository;
    this.slotRepository = slotRepository;
    this.appointments = appointments;
  }

  @Transactional(readOnly = true)
  public Page<AvailableSchedule> find(
      Long doctorId,
      Long clinicId,
      Long specialtyId,
      LocalDate fromDate,
      LocalDate toDate,
      int durationMinutes,
      Long shiftId,
      Pageable pageable) {
    if (durationMinutes != 20 && durationMinutes != 30) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_CAPACITY, "Thời lượng khám phải là 20 hoặc 30 phút");
    }
    ScheduleService.validateDateRange(fromDate, toDate);
    if (fromDate == null || toDate == null) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_DATE_RANGE, "Cần nhập fromDate và toDate");
    }
    Page<ScheduleDtos.ScheduleResponse> matches =
        schedules.list(
            doctorId,
            clinicId,
            specialtyId,
            fromDate,
            toDate,
            ScheduleStatus.OPEN,
            shiftId,
            pageable);
    return matches.map(
        response -> {
          DoctorSchedule schedule =
              scheduleRepository.findByPublicId(response.publicId()).orElseThrow();
          ReservationSnapshot reserved = appointments.reservations(schedule.getId());
          List<DoctorScheduleSlot> slots =
              slotRepository.findAllByScheduleIdOrderByStartAt(schedule.getId());
          List<AvailableWindow> windows =
              windows(schedule, response.clinicId(), slots, reserved, durationMinutes);
          return new AvailableSchedule(response, windows, windows.isEmpty());
        });
  }

  static List<AvailableWindow> windows(
      DoctorSchedule schedule,
      Long clinicId,
      List<DoctorScheduleSlot> slots,
      ReservationSnapshot reserved,
      int durationMinutes) {
    if ((durationMinutes == 20 && reserved.generalVisits() >= schedule.getGeneralCapacity())
        || (durationMinutes == 30
            && reserved.specialistVisits() >= schedule.getSpecialistCapacity())) {
      return List.of();
    }
    int required = durationMinutes / 10;
    Set<Long> occupied = reserved.occupiedSlotIds();
    List<AvailableWindow> result = new ArrayList<>();
    for (int start = 0; start + required <= slots.size(); start++) {
      boolean free = true;
      Instant end = slots.get(start).getStartAt();
      for (int offset = 0; offset < required; offset++) {
        DoctorScheduleSlot slot = slots.get(start + offset);
        if (slot.getStatus() != SlotStatus.AVAILABLE
            || occupied.contains(slot.getId())
            || !slot.getStartAt().equals(end)) {
          free = false;
          break;
        }
        end = slot.getEndAt();
      }
      if (free) {
        result.add(
            new AvailableWindow(
                schedule.getPublicId(),
                schedule.getDoctorId(),
                schedule.getSpecialtyId(),
                clinicId,
                schedule.getRoomId(),
                slots.get(start).getStartAt(),
                end,
                durationMinutes));
      }
    }
    return result;
  }
}
