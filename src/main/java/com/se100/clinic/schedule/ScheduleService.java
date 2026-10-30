package com.se100.clinic.schedule;

import com.se100.clinic.appointment.AppointmentSchedulingService;
import com.se100.clinic.appointment.AppointmentSchedulingService.ReservationSnapshot;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService.RoomAssignment;
import com.se100.clinic.schedule.ScheduleDtos.ChangeCapacityRequest;
import com.se100.clinic.schedule.ScheduleDtos.DoctorQueueResponse;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import com.se100.clinic.schedule.ScheduleDtos.ShiftResponse;
import com.se100.clinic.schedule.ScheduleDtos.SlotResponse;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.schedule.enums.SlotStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Admin assignment and doctor schedule reads. Booking owns reservations and patient conflicts. */
@Service
public class ScheduleService {
  public static final Set<String> SORTABLE_FIELDS =
      Set.of("workDate", "startAt", "status", "createdAt");
  public static final Sort DEFAULT_SORT = Sort.by("workDate", "startAt");

  private final ShiftRepository shiftRepository;
  private final DoctorScheduleRepository scheduleRepository;
  private final DoctorScheduleSlotRepository slotRepository;
  private final DoctorSchedulingService doctors;
  private final FacilitySchedulingService facilities;
  private final AppointmentSchedulingService appointments;
  private final ZoneId zone;

  public ScheduleService(
      ShiftRepository shiftRepository,
      DoctorScheduleRepository scheduleRepository,
      DoctorScheduleSlotRepository slotRepository,
      DoctorSchedulingService doctors,
      FacilitySchedulingService facilities,
      AppointmentSchedulingService appointments,
      @Value("${app.timezone}") String timezone) {
    this.shiftRepository = shiftRepository;
    this.scheduleRepository = scheduleRepository;
    this.slotRepository = slotRepository;
    this.doctors = doctors;
    this.facilities = facilities;
    this.appointments = appointments;
    this.zone = ZoneId.of(timezone);
  }

  @Transactional(readOnly = true)
  public List<ShiftResponse> shifts() {
    return shiftRepository.findAll(Sort.by("startTime")).stream().map(ShiftResponse::from).toList();
  }

  @Transactional
  public ScheduleResponse create(SaveScheduleRequest request) {
    Shift shift = requireShift(request.shiftId());
    Instant start = startAt(request.workDate(), shift);
    Instant end = endAt(request.workDate(), shift);
    validateAssignment(request, shift, start, end, null);
    DoctorSchedule schedule = new DoctorSchedule();
    apply(schedule, request, shift, start, end);
    try {
      scheduleRepository.saveAndFlush(schedule);
      slotRepository.saveAllAndFlush(generateSlots(schedule));
    } catch (DataIntegrityViolationException error) {
      throw translateOverlap(error);
    }
    return ScheduleResponse.from(schedule, request.clinicId());
  }

  @Transactional
  public ScheduleResponse update(UUID publicId, SaveScheduleRequest request) {
    DoctorSchedule schedule = requireScheduleForUpdate(publicId);
    if (appointments.hasAppointments(schedule.getId())) {
      throw new ConflictException(
          ScheduleErrorCode.SCHEDULE_HAS_APPOINTMENTS,
          "Không thể thay đổi phân công đã có lịch hẹn; hãy giữ lịch sử và tạo ca khác");
    }
    Shift shift = requireShift(request.shiftId());
    Instant start = startAt(request.workDate(), shift);
    Instant end = endAt(request.workDate(), shift);
    validateAssignment(request, shift, start, end, schedule.getId());
    apply(schedule, request, shift, start, end);
    try {
      scheduleRepository.flush();
      slotRepository.deleteForSchedule(schedule.getId());
      slotRepository.saveAllAndFlush(generateSlots(schedule));
    } catch (DataIntegrityViolationException error) {
      throw translateOverlap(error);
    }
    return ScheduleResponse.from(schedule, request.clinicId());
  }

  @Transactional
  public ScheduleResponse changeStatus(UUID publicId, ScheduleStatus status) {
    DoctorSchedule schedule = requireScheduleForUpdate(publicId);
    if (status == ScheduleStatus.CANCELLED && appointments.hasAppointments(schedule.getId())) {
      throw new ConflictException(
          ScheduleErrorCode.SCHEDULE_HAS_APPOINTMENTS, "Không thể hủy ca đã có lịch hẹn");
    }
    if (status == ScheduleStatus.OPEN) {
      validateResources(
          schedule.getDoctorId(), schedule.getSpecialtyId(), schedule.getRoomId(), null);
      ensureNoOverlap(
          schedule.getDoctorId(),
          schedule.getRoomId(),
          schedule.getStartAt(),
          schedule.getEndAt(),
          schedule.getId());
    }
    schedule.setStatus(status);
    try {
      scheduleRepository.flush();
    } catch (DataIntegrityViolationException error) {
      throw translateOverlap(error);
    }
    return toResponse(schedule);
  }

  @Transactional
  public ScheduleResponse changeCapacity(UUID publicId, ChangeCapacityRequest request) {
    DoctorSchedule schedule = requireScheduleForUpdate(publicId);
    validateCapacity(schedule.getShift(), request.generalCapacity(), request.specialistCapacity());
    ReservationSnapshot reserved = appointments.reservations(schedule.getId());
    if (request.generalCapacity() < reserved.generalVisits()
        || request.specialistCapacity() < reserved.specialistVisits()) {
      throw new ConflictException(
          ScheduleErrorCode.CAPACITY_BELOW_BOOKED,
          "Giới hạn mới thấp hơn số lượt đang giữ chỗ trong ca");
    }
    schedule.setGeneralCapacity(request.generalCapacity());
    schedule.setSpecialistCapacity(request.specialistCapacity());
    scheduleRepository.flush();
    return toResponse(schedule);
  }

  @Transactional(readOnly = true)
  public ScheduleResponse get(UUID publicId) {
    return toResponse(requireSchedule(publicId));
  }

  @Transactional(readOnly = true)
  public DoctorQueueResponse doctorQueue(UUID publicId, Long doctorId) {
    DoctorSchedule schedule = requireSchedule(publicId);
    if (!schedule.getDoctorId().equals(doctorId)) {
      throw new NotFoundException(
          ScheduleErrorCode.SCHEDULE_NOT_ASSIGNED, "Không tìm thấy ca được phân công");
    }
    return new DoctorQueueResponse(
        toResponse(schedule), appointments.doctorQueue(schedule.getId()));
  }

  @Transactional(readOnly = true)
  public Page<ScheduleResponse> list(
      Long doctorId,
      Long clinicId,
      Long specialtyId,
      LocalDate fromDate,
      LocalDate toDate,
      ScheduleStatus status,
      Long shiftId,
      Pageable pageable) {
    return list(doctorId, clinicId, specialtyId, fromDate, toDate, status, shiftId, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ScheduleResponse> list(
      Long doctorId,
      Long clinicId,
      Long specialtyId,
      LocalDate fromDate,
      LocalDate toDate,
      ScheduleStatus status,
      Long shiftId,
      Instant endingAfter,
      Pageable pageable) {
    validateDateRange(fromDate, toDate);
    // Availability filters resources before pagination, so totals and page boundaries remain valid.
    List<Long> workingDoctorIds = endingAfter == null ? null : doctors.workingDoctorIds();
    List<Long> roomIds =
        endingAfter != null
            ? facilities.openRoomIds(clinicId)
            : clinicId == null ? null : facilities.roomIds(clinicId);
    if (workingDoctorIds != null && workingDoctorIds.isEmpty()) {
      return Page.empty(pageable);
    }
    if (roomIds != null && roomIds.isEmpty()) {
      return Page.empty(pageable);
    }
    Specification<DoctorSchedule> spec =
        (root, query, cb) -> {
          List<Predicate> filters = new ArrayList<>();
          if (workingDoctorIds != null) {
            filters.add(root.get("doctorId").in(workingDoctorIds));
          }
          if (doctorId != null) {
            filters.add(cb.equal(root.get("doctorId"), doctorId));
          }
          if (roomIds != null) {
            filters.add(root.get("roomId").in(roomIds));
          }
          if (specialtyId != null) {
            filters.add(cb.equal(root.get("specialtyId"), specialtyId));
          }
          if (fromDate != null) {
            filters.add(cb.greaterThanOrEqualTo(root.get("workDate"), fromDate));
          }
          if (toDate != null) {
            filters.add(cb.lessThanOrEqualTo(root.get("workDate"), toDate));
          }
          if (status != null) {
            filters.add(cb.equal(root.get("status"), status));
          }
          if (shiftId != null) {
            filters.add(cb.equal(root.get("shift").get("id"), shiftId));
          }
          if (endingAfter != null) {
            filters.add(cb.greaterThan(root.get("endAt"), endingAfter));
          }
          return cb.and(filters.toArray(Predicate[]::new));
        };
    return scheduleRepository.findAll(spec, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public List<SlotResponse> slots(UUID publicId) {
    DoctorSchedule schedule = requireSchedule(publicId);
    Set<Long> occupied = appointments.reservations(schedule.getId()).occupiedSlotIds();
    return slotRepository.findAllByScheduleIdOrderByStartAt(schedule.getId()).stream()
        .map(slot -> SlotResponse.from(slot, occupied.contains(slot.getId())))
        .toList();
  }

  @Transactional
  public SlotResponse changeSlotStatus(UUID publicId, Long slotId, SlotStatus status) {
    DoctorSchedule schedule = requireScheduleForUpdate(publicId);
    DoctorScheduleSlot slot =
        slotRepository
            .findByIdAndScheduleId(slotId, schedule.getId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ScheduleErrorCode.SLOT_NOT_FOUND, "Không tìm thấy ô giờ"));
    boolean occupied =
        appointments.reservations(schedule.getId()).occupiedSlotIds().contains(slotId);
    if (occupied && status == SlotStatus.BLOCKED) {
      throw new ConflictException(ScheduleErrorCode.SLOT_OCCUPIED, "Ô giờ đã có lịch hẹn");
    }
    slot.setStatus(status);
    return SlotResponse.from(slot, occupied);
  }

  DoctorSchedule requireSchedule(UUID publicId) {
    return scheduleRepository
        .findByPublicId(publicId)
        .orElseThrow(
            () -> new NotFoundException(ScheduleErrorCode.SCHEDULE_NOT_FOUND, "Không tìm thấy ca"));
  }

  private DoctorSchedule requireScheduleForUpdate(UUID publicId) {
    return scheduleRepository
        .findForUpdateByPublicId(publicId)
        .orElseThrow(
            () -> new NotFoundException(ScheduleErrorCode.SCHEDULE_NOT_FOUND, "Không tìm thấy ca"));
  }

  /** Booking must call this in the transaction that writes appointment_slots. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Long> lockSlotsForBooking(Long scheduleId, Instant start, Instant end) {
    if (start == null
        || end == null
        || start.isBefore(Instant.now())
        || !(end.equals(start.plusSeconds(1200)) || end.equals(start.plusSeconds(1800)))) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_WINDOW, "Khoảng khám phải là 20 hoặc 30 phút ở tương lai");
    }
    DoctorSchedule schedule = lockSchedule(scheduleId);
    if (schedule.getStatus() != ScheduleStatus.OPEN) {
      throw new ConflictException(ScheduleErrorCode.SCHEDULE_NOT_OPEN, "Ca không nhận lịch mới");
    }
    RoomAssignment room =
        validateResources(
            schedule.getDoctorId(), schedule.getSpecialtyId(), schedule.getRoomId(), null);
    List<DoctorScheduleSlot> slots = slotRepository.findAllByScheduleIdOrderByStartAt(scheduleId);
    boolean available =
        ScheduleAvailabilityService.windows(
                schedule,
                room.clinicId(),
                slots,
                appointments.reservations(scheduleId),
                (int) Duration.between(start, end).toMinutes(),
                Instant.now())
            .stream()
            .anyMatch(window -> window.startAt().equals(start) && window.endAt().equals(end));
    if (!available) {
      throw new ConflictException(
          ScheduleErrorCode.SLOT_UNAVAILABLE, "Khung giờ đã hết chỗ hoặc bị chặn");
    }
    return slots.stream()
        .filter(slot -> !slot.getStartAt().isBefore(start) && !slot.getEndAt().isAfter(end))
        .map(DoctorScheduleSlot::getId)
        .toList();
  }

  /** Call before creating/updating appointments; locks persist through the outer transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForBookingChange(Long scheduleId) {
    lockSchedule(scheduleId);
  }

  private DoctorSchedule lockSchedule(Long id) {
    return scheduleRepository
        .findForUpdateById(id)
        .orElseThrow(
            () -> new NotFoundException(ScheduleErrorCode.SCHEDULE_NOT_FOUND, "Không tìm thấy ca"));
  }

  private Shift requireShift(Long id) {
    return shiftRepository
        .findById(id)
        .orElseThrow(
            () ->
                new NotFoundException(ScheduleErrorCode.SHIFT_NOT_FOUND, "Không tìm thấy mẫu ca"));
  }

  private void validateAssignment(
      SaveScheduleRequest request, Shift shift, Instant start, Instant end, Long excludeId) {
    if (!isFixedShift(shift)) {
      throw new BusinessException(ScheduleErrorCode.INVALID_SHIFT, "Chỉ hỗ trợ ba ca cố định");
    }
    validateCapacity(shift, request.generalCapacity(), request.specialistCapacity());
    validateResources(
        request.doctorId(), request.specialtyId(), request.roomId(), request.clinicId());
    ensureNoOverlap(request.doctorId(), request.roomId(), start, end, excludeId);
  }

  private RoomAssignment validateResources(
      Long doctorId, Long specialtyId, Long roomId, Long clinicId) {
    if (!doctors.exists(doctorId)) {
      throw new NotFoundException(ScheduleErrorCode.DOCTOR_NOT_FOUND, "Không tìm thấy bác sĩ");
    }
    if (!doctors.isWorking(doctorId)) {
      throw new ConflictException(
          ScheduleErrorCode.DOCTOR_NOT_WORKING, "Bác sĩ đang ngừng hoạt động");
    }
    if (!doctors.hasSpecialty(doctorId, specialtyId)) {
      throw new ConflictException(
          ScheduleErrorCode.DOCTOR_SPECIALTY_MISMATCH,
          "Bác sĩ không thuộc chuyên khoa được phân công");
    }
    RoomAssignment room =
        facilities
            .roomAssignment(roomId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ScheduleErrorCode.ROOM_NOT_FOUND, "Không tìm thấy phòng"));
    if (clinicId != null && !room.clinicId().equals(clinicId)) {
      throw new ConflictException(
          ScheduleErrorCode.ROOM_CLINIC_MISMATCH, "Phòng không thuộc cơ sở được chọn");
    }
    if (!room.roomOpen() || !room.clinicOpen()) {
      throw new ConflictException(
          ScheduleErrorCode.FACILITY_NOT_OPEN, "Cơ sở hoặc phòng đang ngừng hoạt động");
    }
    return room;
  }

  private static boolean isFixedShift(Shift shift) {
    return switch (shift.getCode()) {
      case "MORNING" ->
          shift.getStartTime().equals(LocalTime.of(8, 0))
              && shift.getEndTime().equals(LocalTime.of(12, 0));
      case "AFTERNOON" ->
          shift.getStartTime().equals(LocalTime.of(13, 0))
              && shift.getEndTime().equals(LocalTime.of(17, 0));
      case "EVENING" ->
          shift.getStartTime().equals(LocalTime.of(17, 0))
              && shift.getEndTime().equals(LocalTime.of(20, 0));
      default -> false;
    };
  }

  private static void validateCapacity(Shift shift, int generalCapacity, int specialistCapacity) {
    if (!isFixedShift(shift)) {
      throw new BusinessException(ScheduleErrorCode.INVALID_SHIFT, "Chỉ hỗ trợ ba ca cố định");
    }
    int minutes = (int) Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
    if (generalCapacity > minutes / 20 || specialistCapacity > minutes / 30) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_CAPACITY, "Giới hạn lượt vượt thời lượng ca");
    }
  }

  private void ensureNoOverlap(
      Long doctorId, Long roomId, Instant start, Instant end, Long excludeId) {
    if (scheduleRepository.overlapsDoctor(doctorId, start, end, ScheduleStatus.CANCELLED, excludeId)
        || scheduleRepository.overlapsRoom(
            roomId, start, end, ScheduleStatus.CANCELLED, excludeId)) {
      throw new ConflictException(
          ScheduleErrorCode.SCHEDULE_OVERLAP, "Bác sĩ hoặc phòng đã được phân công trong giờ này");
    }
  }

  private static RuntimeException translateOverlap(DataIntegrityViolationException error) {
    if (DbErrors.isExclusionViolation(error)) {
      return new ConflictException(
          ScheduleErrorCode.SCHEDULE_OVERLAP, "Bác sĩ hoặc phòng đã được phân công trong giờ này");
    }
    return error;
  }

  private static void apply(
      DoctorSchedule schedule,
      SaveScheduleRequest request,
      Shift shift,
      Instant start,
      Instant end) {
    schedule.setDoctorId(request.doctorId());
    schedule.setSpecialtyId(request.specialtyId());
    schedule.setRoomId(request.roomId());
    schedule.setShift(shift);
    schedule.setWorkDate(request.workDate());
    schedule.setStartAt(start);
    schedule.setEndAt(end);
    schedule.setGeneralCapacity(request.generalCapacity());
    schedule.setSpecialistCapacity(request.specialistCapacity());
  }

  private static List<DoctorScheduleSlot> generateSlots(DoctorSchedule schedule) {
    List<DoctorScheduleSlot> slots = new ArrayList<>();
    for (Instant at = schedule.getStartAt();
        at.isBefore(schedule.getEndAt());
        at = at.plusSeconds(600)) {
      DoctorScheduleSlot slot = new DoctorScheduleSlot();
      slot.setSchedule(schedule);
      slot.setStartAt(at);
      slot.setEndAt(at.plusSeconds(600));
      slots.add(slot);
    }
    return slots;
  }

  private Instant startAt(LocalDate date, Shift shift) {
    return date.atTime(shift.getStartTime()).atZone(zone).toInstant();
  }

  private Instant endAt(LocalDate date, Shift shift) {
    return date.atTime(shift.getEndTime()).atZone(zone).toInstant();
  }

  private ScheduleResponse toResponse(DoctorSchedule schedule) {
    Long clinicId =
        facilities
            .roomAssignment(schedule.getRoomId())
            .orElseThrow(
                () ->
                    new NotFoundException(ScheduleErrorCode.ROOM_NOT_FOUND, "Không tìm thấy phòng"))
            .clinicId();
    return ScheduleResponse.from(schedule, clinicId);
  }

  static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_DATE_RANGE, "fromDate phải trước hoặc bằng toDate");
    }
  }
}
