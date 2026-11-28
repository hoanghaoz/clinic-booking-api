package com.se100.clinic.appointment;

import com.se100.clinic.appointment.AppointmentManagementDtos.Action;
import com.se100.clinic.appointment.AppointmentManagementDtos.ChangeRequest;
import com.se100.clinic.appointment.AppointmentManagementDtos.ClinicOption;
import com.se100.clinic.appointment.AppointmentManagementDtos.DeskBookingRequest;
import com.se100.clinic.appointment.AppointmentManagementDtos.Detail;
import com.se100.clinic.appointment.AppointmentManagementDtos.Group;
import com.se100.clinic.appointment.AppointmentManagementDtos.Summary;
import com.se100.clinic.appointment.enums.AppointmentAction;
import com.se100.clinic.appointment.enums.AppointmentActor;
import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.employee.StaffService;
import com.se100.clinic.patient.PatientBookingService;
import com.se100.clinic.patient.PatientBookingService.BookingProfile;
import com.se100.clinic.schedule.ScheduleAvailabilityService;
import com.se100.clinic.schedule.ScheduleDtos.AvailableSchedule;
import com.se100.clinic.schedule.ScheduleErrorCode;
import com.se100.clinic.schedule.ScheduleService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AppointmentManagementService {
  private final AppointmentManagementQueries queries;
  private final AppointmentRepository appointments;
  private final AppointmentBookingService booking;
  private final AppointmentReservationService reservations;
  private final PatientBookingService patients;
  private final StaffService staff;
  private final ScheduleService schedules;
  private final ScheduleAvailabilityService availability;
  private final EntityManager em;
  private final ZoneId timezone;

  AppointmentManagementService(
      AppointmentManagementQueries queries,
      AppointmentRepository appointments,
      AppointmentBookingService booking,
      AppointmentReservationService reservations,
      PatientBookingService patients,
      StaffService staff,
      ScheduleService schedules,
      ScheduleAvailabilityService availability,
      EntityManager em,
      @Value("${app.timezone}") String timezone) {
    this.queries = queries;
    this.appointments = appointments;
    this.booking = booking;
    this.reservations = reservations;
    this.patients = patients;
    this.staff = staff;
    this.schedules = schedules;
    this.availability = availability;
    this.em = em;
    this.timezone = ZoneId.of(timezone);
  }

  Page<Summary> mine(Long user, Group group, Pageable page) {
    return queries.forPatient(profile(user).id(), group, Instant.now(), page);
  }

  List<ClinicOption> clinics(Long user) {
    return queries.clinics(staffId(user));
  }

  Page<Summary> desk(
      Long user, Long clinic, String term, LocalDate date, boolean daily, Pageable page) {
    Long employee = staffId(user);
    requireClinic(employee, clinic);
    LocalDate day = date == null && daily ? LocalDate.now(timezone) : date;
    Instant from = day == null ? null : day.atStartOfDay(timezone).toInstant();
    Instant to = day == null ? null : day.plusDays(1).atStartOfDay(timezone).toInstant();
    return queries.forStaff(
        employee, clinic, term == null ? "" : term.strip(), from, to, daily, Instant.now(), page);
  }

  Detail detail(Long user, boolean atDesk, UUID id) {
    Appointment current = appointment(id);
    Long employee = atDesk ? staffId(user) : null;
    if (!atDesk) requireOwner(profile(user), current);
    return fresh(id, employee);
  }

  Page<AvailableSchedule> times(
      Long user,
      boolean atDesk,
      UUID id,
      Long clinic,
      LocalDate date,
      Integer duration,
      Pageable page) {
    Long employee = atDesk ? staffId(user) : null;
    Long specialty = null;
    if (id != null) {
      Appointment current = appointment(id);
      var schedule = schedules.getById(current.getScheduleId());
      if (atDesk) {
        requireClinic(employee, schedule.clinicId());
      } else {
        requireOwner(profile(user), current);
      }
      specialty = schedule.specialtyId();
      duration =
          Math.toIntExact(Duration.between(current.getStartAt(), current.getEndAt()).toMinutes());
    }
    if (atDesk) requireClinic(employee, clinic);
    return availability.find(
        null, clinic, specialty, date, date, duration == null ? 20 : duration, null, page);
  }

  @Transactional
  BookingDtos.Ticket bookAtDesk(Long user, DeskBookingRequest request) {
    Long employee = staffId(user);
    requireClinic(employee, request.booking().clinicId());
    Long patient =
        queries
            .patientId(request.patientPublicId())
            .orElseThrow(
                () -> new NotFoundException(CommonErrorCode.NOT_FOUND, "Không tìm thấy bệnh nhân"));
    return booking.bookAtDesk(patient, employee, request.booking());
  }

  @Transactional
  Detail change(Long user, boolean atDesk, UUID id, ChangeRequest request) {
    Long employee = atDesk ? staffId(user) : null;
    Appointment current = appointment(id);
    if (atDesk) {
      requireClinic(employee, queries.clinicOfSchedule(current.getScheduleId()));
    } else {
      requireOwner(profile(user), current);
      if (request.action() == Action.CONFIRM) throw forbidden("Chỉ nhân viên được xác nhận lịch");
    }
    BookingProfile patient =
        patients
            .lockForPatient(current.getPatientId())
            .orElseThrow(AppointmentManagementService::notFound);
    Long oldSchedule = current.getScheduleId();
    Long destination = request.action() == Action.RESCHEDULE ? target(request) : oldSchedule;
    // Match the reservation writer's lock order.
    Stream.of(oldSchedule, destination)
        .distinct()
        .sorted()
        .forEach(schedules::lockForBookingChange);
    em.refresh(current, LockModeType.PESSIMISTIC_WRITE);
    if (!current.getScheduleId().equals(oldSchedule)) {
      throw new ConflictException(
          ReservationErrorCode.APPOINTMENT_CHANGED, "Lịch hẹn vừa thay đổi, vui lòng thử lại");
    }
    var original = schedules.getById(oldSchedule);
    if (atDesk) requireClinic(employee, original.clinicId());
    Instant now = Instant.now();
    if (!current.getStartAt().isAfter(now)
        || (current.getStatus() != AppointmentStatus.CONFIRMED
            && (current.getStatus() != AppointmentStatus.HELD
                || current.getHoldExpiresAt() == null
                || !current.getHoldExpiresAt().isAfter(now)))) {
      throw new ConflictException(
          ReservationErrorCode.APPOINTMENT_NOT_BOOKABLE,
          "Chỉ xử lý lịch chưa đến giờ và còn hiệu lực");
    }
    long hours = queries.cancellationHours(current.getId());
    boolean late = now.isAfter(current.getStartAt().minus(Duration.ofHours(hours)));
    AppointmentHistory history = before(current, employee, request.reason());
    switch (request.action()) {
      case CANCEL -> {
        current.setStatus(AppointmentStatus.CANCELLED);
        current.setHoldExpiresAt(null);
        history.setAction(late ? AppointmentAction.LATE_CANCELLED : AppointmentAction.CANCELLED);
        reservations.release(current.getId());
      }
      case CONFIRM -> {
        if (current.getStatus() == AppointmentStatus.CONFIRMED) return fresh(id, employee);
        if (!queries.hasReservations(current.getId())) reservations.reserve(current.getId());
        current.setStatus(AppointmentStatus.CONFIRMED);
        current.setHoldExpiresAt(null);
        history.setAction(AppointmentAction.CONFIRMED);
      }
      case RESCHEDULE -> {
        if (oldSchedule.equals(destination) && current.getStartAt().equals(request.startAt())) {
          return fresh(id, employee);
        }
        if (late && !atDesk) {
          throw new ConflictException(
              ReservationErrorCode.APPOINTMENT_NOT_BOOKABLE,
              "Đã qua hạn tự đổi lịch. Vui lòng liên hệ nhân viên");
        }
        var next = schedules.lockForBooking(request.schedulePublicId()).schedule();
        if (atDesk) requireClinic(employee, next.clinicId());
        if (!original.specialtyId().equals(next.specialtyId())) {
          throw new ConflictException(
              BookingErrorCode.BOOKING_SPECIALTY_MISMATCH,
              "Ca mới phải cùng chuyên khoa với ca cũ");
        }
        Instant end =
            request.startAt().plus(Duration.between(current.getStartAt(), current.getEndAt()));
        booking.validateMovedBooking(patient, next, request.startAt(), end, now, current.getId());
        reservations.move(current.getId(), destination, request.startAt(), end);
        history.setAction(AppointmentAction.RESCHEDULED);
      }
    }
    history.setNewStatus(current.getStatus());
    history.setNewScheduleId(current.getScheduleId());
    history.setNewStartAt(current.getStartAt());
    history.setNewEndAt(current.getEndAt());
    em.persist(history);
    em.flush();
    return fresh(id, employee);
  }

  private Detail fresh(UUID id, Long employee) {
    return queries
        .detail(id, Instant.now(), employee)
        .orElseThrow(
            () ->
                employee == null
                    ? notFound()
                    : forbidden("Bạn không được phân công tại cơ sở này"));
  }

  private Long target(ChangeRequest request) {
    if (request.schedulePublicId() == null || request.startAt() == null) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Cần chọn ca và giờ khám mới");
    }
    return queries
        .scheduleId(request.schedulePublicId())
        .orElseThrow(
            () -> new NotFoundException(ScheduleErrorCode.SCHEDULE_NOT_FOUND, "Không tìm thấy ca"));
  }

  private static AppointmentHistory before(Appointment current, Long employee, String reason) {
    AppointmentHistory history = new AppointmentHistory();
    history.setAppointment(current);
    history.setActor(employee == null ? AppointmentActor.PATIENT : AppointmentActor.STAFF);
    history.setActorPatientId(employee == null ? current.getPatientId() : null);
    history.setActorStaffId(employee);
    history.setOldStatus(current.getStatus());
    history.setOldScheduleId(current.getScheduleId());
    history.setOldStartAt(current.getStartAt());
    history.setOldEndAt(current.getEndAt());
    history.setReason(reason == null || reason.isBlank() ? null : reason.strip());
    return history;
  }

  private Appointment appointment(UUID id) {
    return appointments.findByPublicId(id).orElseThrow(AppointmentManagementService::notFound);
  }

  private BookingProfile profile(Long user) {
    return patients
        .forUser(user)
        .orElseThrow(
            () ->
                new ConflictException(
                    BookingErrorCode.PATIENT_PROFILE_REQUIRED, "Cần liên kết hồ sơ bệnh nhân"));
  }

  private Long staffId(Long user) {
    return staff
        .findWorkingStaffIdByUserId(user)
        .orElseThrow(() -> forbidden("Cần hồ sơ nhân viên đang hoạt động"));
  }

  private void requireClinic(Long employee, Long clinic) {
    if (!queries.assigned(employee, clinic))
      throw forbidden("Bạn không được phân công tại cơ sở này");
  }

  private static void requireOwner(BookingProfile patient, Appointment appointment) {
    if (!appointment.getPatientId().equals(patient.id()))
      throw forbidden("Bạn không có quyền truy cập lịch hẹn này");
  }

  private static BusinessException forbidden(String message) {
    return new BusinessException(CommonErrorCode.FORBIDDEN, message);
  }

  private static NotFoundException notFound() {
    return new NotFoundException(
        ReservationErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn");
  }
}
