package com.se100.clinic.appointment;

import com.se100.clinic.appointment.BookingDtos.Availability;
import com.se100.clinic.appointment.BookingDtos.CreateRequest;
import com.se100.clinic.appointment.BookingDtos.ReceptionReference;
import com.se100.clinic.appointment.BookingDtos.Ticket;
import com.se100.clinic.appointment.enums.AppointmentAction;
import com.se100.clinic.appointment.enums.AppointmentActor;
import com.se100.clinic.appointment.enums.AppointmentSource;
import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.catalog.CatalogBookingService;
import com.se100.clinic.catalog.CatalogBookingService.Product;
import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService;
import com.se100.clinic.patient.PatientBookingService;
import com.se100.clinic.patient.PatientBookingService.BookingProfile;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.schedule.ScheduleAvailabilityService;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import com.se100.clinic.schedule.ScheduleErrorCode;
import com.se100.clinic.schedule.ScheduleService;
import com.se100.clinic.schedule.ScheduleService.BookingSchedule;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import com.se100.clinic.violation.BookingRestrictionService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AppointmentBookingService {
  private static final int PERSONAL_ADVANCE_DAYS = 30;
  private static final String CANCELLATION_POLICY =
      """
      {"freeCancellationHours":24,"eligibleDepositRefundPercent":100}
      """;

  private final PatientBookingService patients;
  private final BookingRestrictionService restrictions;
  private final CatalogBookingService catalog;
  private final ScheduleService schedules;
  private final ScheduleAvailabilityService availability;
  private final AppointmentRepository appointments;
  private final AppointmentReservationService reservations;
  private final DoctorSchedulingService doctors;
  private final FacilitySchedulingService facilities;
  private final EntityManager entityManager;
  private final ZoneId zone;

  public AppointmentBookingService(
      PatientBookingService patients,
      BookingRestrictionService restrictions,
      CatalogBookingService catalog,
      ScheduleService schedules,
      ScheduleAvailabilityService availability,
      AppointmentRepository appointments,
      AppointmentReservationService reservations,
      DoctorSchedulingService doctors,
      FacilitySchedulingService facilities,
      EntityManager entityManager,
      @Value("${app.timezone}") String timezone) {
    this.patients = patients;
    this.restrictions = restrictions;
    this.catalog = catalog;
    this.schedules = schedules;
    this.availability = availability;
    this.appointments = appointments;
    this.reservations = reservations;
    this.doctors = doctors;
    this.facilities = facilities;
    this.entityManager = entityManager;
    this.zone = ZoneId.of(timezone);
  }

  @Transactional(readOnly = true)
  public Page<Availability> available(
      Long userId,
      Long clinicId,
      Long doctorId,
      Long specialtyId,
      UUID packagePublicId,
      UUID servicePublicId,
      Integer durationMinutes,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable) {
    if (fromDate.isAfter(toDate)) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_DATE_RANGE, "fromDate phải trước hoặc bằng toDate");
    }
    if (packagePublicId != null && servicePublicId != null) {
      throw invalidProduct();
    }
    BookingProfile patient =
        patients.forUser(userId).orElseThrow(AppointmentBookingService::profileRequired);
    Instant now = Instant.now();
    requireOnlineBooking(patient, now);
    LocalDate latest = lastBookableDate(patient, now);
    LocalDate today = now.atZone(zone).toLocalDate();
    LocalDate first = fromDate.isBefore(today) ? today : fromDate;
    LocalDate last = toDate.isAfter(latest) ? latest : toDate;
    Product product = null;
    if (packagePublicId != null) {
      product = catalog.packageForAvailability(packagePublicId);
    } else if (servicePublicId != null) {
      product = catalog.serviceForAvailability(servicePublicId);
    }
    int minutes = durationMinutes == null ? 20 : durationMinutes;
    Set<Long> specialtyIds = specialtyId == null ? Set.of() : Set.of(specialtyId);
    if (product != null) {
      if (durationMinutes != null && minutes != product.durationMinutes()) {
        throw invalidProduct();
      }
      minutes = product.durationMinutes();
      requireGender(patient, product);
      if (packagePublicId != null) {
        if (specialtyId != null) {
          requireSpecialty(product, specialtyId);
        } else {
          specialtyIds = product.specialtyIds();
        }
      }
      if (product.minAge() != null) {
        LocalDate minimum = patient.dateOfBirth().plusYears(product.minAge());
        first = first.isBefore(minimum) ? minimum : first;
      }
      if (product.maxAge() != null) {
        LocalDate maximum = patient.dateOfBirth().plusYears(product.maxAge() + 1L).minusDays(1);
        last = last.isAfter(maximum) ? maximum : last;
      }
    }
    if (minutes != 20 && minutes != 30) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_CAPACITY, "Thời lượng khám phải là 20 hoặc 30 phút");
    }
    if (first.isAfter(last)) {
      return Page.empty(pageable);
    }
    Set<Long> activeSpecialties = Set.copyOf(doctors.activeSpecialtyIds(specialtyIds));
    if (activeSpecialties.isEmpty()) {
      return Page.empty(pageable);
    }
    Product quote = product;
    return availability
        .findForSpecialties(doctorId, clinicId, activeSpecialties, first, last, minutes, pageable)
        .map(
            item ->
                new Availability(
                    item.schedule(),
                    item.windows(),
                    item.full(),
                    quote == null ? null : quote.version(),
                    quote == null ? null : quote.price()));
  }

  @Transactional
  public Ticket bookOnline(@NotNull Long userId, @NotNull @Valid CreateRequest request) {
    BookingProfile patient =
        patients.lockForUser(userId).orElseThrow(AppointmentBookingService::profileRequired);
    return book(patient, null, request);
  }

  /** The front-desk caller must verify the staff member's permission and clinic scope. */
  @Transactional
  public Ticket bookAtDesk(
      @NotNull @Positive Long patientId,
      @NotNull @Positive Long staffId,
      @NotNull @Valid CreateRequest request) {
    BookingProfile patient =
        patients.lockForPatient(patientId).orElseThrow(AppointmentBookingService::profileRequired);
    return book(patient, staffId, request);
  }

  private Ticket book(BookingProfile patient, Long staffId, CreateRequest request) {
    Product product = lockProduct(request);
    BookingSchedule booking = schedules.lockForBooking(request.schedulePublicId());
    ScheduleResponse schedule = booking.schedule();
    Instant now = Instant.now();
    if (staffId == null) {
      requireOnlineBooking(patient, now);
    }
    if (!schedule.clinicId().equals(request.clinicId())
        || !schedule.doctorId().equals(request.doctorId())) {
      throw new ConflictException(
          BookingErrorCode.BOOKING_SCHEDULE_CHANGED, "Ca khám đã đổi bác sĩ hoặc cơ sở");
    }
    if (!request.startAt().isAfter(now)) {
      throw new BusinessException(BookingErrorCode.BOOKING_START_IN_PAST, "Giờ khám đã qua");
    }
    LocalDate visitDate = request.startAt().atZone(zone).toLocalDate();
    if (visitDate.isAfter(lastBookableDate(patient, now))) {
      throw new BusinessException(
          BookingErrorCode.BOOKING_DATE_LIMIT,
          "Bệnh nhân cá nhân chỉ được đặt trước tối đa 30 ngày");
    }
    if (!visitDate.equals(schedule.workDate())) {
      throw new BusinessException(
          ScheduleErrorCode.INVALID_WINDOW, "Giờ khám không thuộc ngày của ca");
    }
    requireSpecialty(product, schedule.specialtyId());
    requireGender(patient, product);
    if (visitDate.isBefore(patient.dateOfBirth())
        || (product.minAge() != null
            && visitDate.isBefore(patient.dateOfBirth().plusYears(product.minAge())))
        || (product.maxAge() != null
            && !visitDate.isBefore(patient.dateOfBirth().plusYears(product.maxAge() + 1L)))) {
      throw ineligibleProduct();
    }
    Instant endAt = request.startAt().plusSeconds(product.durationMinutes() * 60L);
    requireNoConflict(patient.id(), schedule, request.startAt(), endAt, now);
    Appointment appointment = new Appointment();
    appointment.setCode(
        "AP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
    appointment.setQrTokenHash(BookingQr.hash(appointment.getCode()));
    appointment.setPatientId(patient.id());
    appointment.setScheduleId(booking.id());
    appointment.setPackageVersionId(product.packageVersionId());
    appointment.setServiceId(product.serviceId());
    appointment.setStartAt(request.startAt());
    appointment.setEndAt(endAt);
    appointment.setSource(
        staffId == null ? AppointmentSource.ONLINE : AppointmentSource.FRONT_DESK);
    appointment.setCreatedByStaffId(staffId);
    appointment.setPriceSnapshot(product.price());
    appointment.setPreparationSnapshot(product.instruction());
    appointment.setCancellationPolicySnapshot(CANCELLATION_POLICY);
    appointments.save(appointment);
    reservations.reserve(appointment.getId());
    recordCreation(appointment, staffId);
    return ticket(appointment, schedule);
  }

  @Transactional(readOnly = true)
  public Ticket ticketForUser(Long userId, UUID publicId) {
    BookingProfile patient =
        patients.forUser(userId).orElseThrow(AppointmentBookingService::profileRequired);
    Appointment appointment =
        appointments
            .findByPublicIdAndPatientId(publicId, patient.id())
            .orElseThrow(AppointmentBookingService::notFound);
    return ticket(appointment, schedules.getById(appointment.getScheduleId()));
  }

  /** Internal QR resolution. The reception caller supplies a clinic it is authorized to access. */
  @Transactional(readOnly = true)
  public Optional<ReceptionReference> findByQr(String payload, @NotNull @Positive Long clinicId) {
    if (payload == null || !payload.matches("AP-[0-9A-F]{32}")) {
      return Optional.empty();
    }
    return appointments
        .findByQrTokenHash(BookingQr.hash(payload))
        .filter(a -> schedules.getById(a.getScheduleId()).clinicId().equals(clinicId))
        .map(
            a ->
                new ReceptionReference(
                    a.getPublicId(),
                    a.getCode(),
                    a.getPatientId(),
                    a.getStatus(),
                    a.getStartAt(),
                    a.getEndAt()));
  }

  private Product lockProduct(CreateRequest request) {
    if (request.packagePublicId() != null
        && request.servicePublicId() == null
        && request.packageVersion() != null
        && !request.packageVersion().isBlank()) {
      return catalog.lockPackageForBooking(request.packagePublicId(), request.packageVersion());
    }
    if (request.servicePublicId() != null
        && request.packagePublicId() == null
        && request.packageVersion() == null) {
      return catalog.lockServiceForBooking(request.servicePublicId());
    }
    throw invalidProduct();
  }

  private void requireOnlineBooking(BookingProfile patient, Instant now) {
    if (patient.patientClass() == PatientClass.COMPANY) {
      throw new BusinessException(
          BookingErrorCode.ONLINE_BOOKING_RESTRICTED,
          "Bệnh nhân doanh nghiệp được xếp lịch theo danh sách doanh nghiệp");
    }
    restrictions
        .restrictedUntil(patient.id(), now)
        .ifPresent(
            until -> {
              throw new BusinessException(
                  BookingErrorCode.ONLINE_BOOKING_RESTRICTED,
                  "Quyền đặt lịch trực tuyến bị khóa đến " + until);
            });
  }

  private LocalDate lastBookableDate(BookingProfile patient, Instant now) {
    return switch (patient.patientClass()) {
      case PERSONAL -> now.atZone(zone).toLocalDate().plusDays(PERSONAL_ADVANCE_DAYS);
      case VIP, COMPANY -> LocalDate.MAX;
    };
  }

  private static void requireSpecialty(Product product, Long specialtyId) {
    if (!product.specialtyIds().isEmpty() && !product.specialtyIds().contains(specialtyId)) {
      throw new ConflictException(
          BookingErrorCode.BOOKING_SPECIALTY_MISMATCH,
          "Chuyên khoa của ca không nhận gói khám này");
    }
  }

  private static void requireGender(BookingProfile patient, Product product) {
    if (product.target() != TargetGender.BOTH
        && !product.target().name().equals(patient.gender().name())) {
      throw ineligibleProduct();
    }
  }

  private void requireNoConflict(
      Long patientId, ScheduleResponse schedule, Instant startAt, Instant endAt, Instant now) {
    if (appointments.overlaps(patientId, startAt, endAt, now)) {
      throw new ConflictException(
          BookingErrorCode.APPOINTMENT_OVERLAP, "Bệnh nhân đã có lịch hẹn trùng giờ");
    }
    LocalDate date = schedule.workDate();
    List<Long> sameDay =
        appointments.activeScheduleIds(
            patientId,
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
            now);
    if (schedules.hasScheduleAtOtherClinic(sameDay, schedule)) {
      throw new ConflictException(
          BookingErrorCode.APPOINTMENT_OTHER_CLINIC_SAME_SHIFT,
          "Bệnh nhân đã có lịch hẹn ở cơ sở khác trong cùng ca");
    }
  }

  private void recordCreation(Appointment appointment, Long staffId) {
    AppointmentHistory history = new AppointmentHistory();
    history.setAppointment(appointment);
    history.setAction(AppointmentAction.CREATED);
    history.setActor(staffId == null ? AppointmentActor.PATIENT : AppointmentActor.STAFF);
    if (staffId == null) {
      history.setActorPatientId(appointment.getPatientId());
    }
    history.setActorStaffId(staffId);
    history.setNewStatus(AppointmentStatus.CONFIRMED);
    history.setNewScheduleId(appointment.getScheduleId());
    history.setNewStartAt(appointment.getStartAt());
    history.setNewEndAt(appointment.getEndAt());
    entityManager.persist(history);
  }

  private Ticket ticket(Appointment appointment, ScheduleResponse schedule) {
    return new Ticket(
        appointment.getPublicId(),
        appointment.getCode(),
        appointment.getQrTokenHash() == null ? null : BookingQr.pngBase64(appointment.getCode()),
        appointment.getStatus(),
        appointment.getStartAt(),
        appointment.getEndAt(),
        schedule.clinicId(),
        facilities.clinicName(schedule.clinicId()),
        schedule.doctorId(),
        doctors.name(schedule.doctorId()),
        appointment.getPriceSnapshot(),
        appointment.getDepositRequired(),
        appointment.getPreparationSnapshot());
  }

  private static BusinessException invalidProduct() {
    return new BusinessException(
        BookingErrorCode.INVALID_BOOKING_PRODUCT,
        "Chọn một dịch vụ hoặc gói khám kèm phiên bản và thời lượng phù hợp");
  }

  private static ConflictException ineligibleProduct() {
    return new ConflictException(
        BookingErrorCode.BOOKING_PRODUCT_INELIGIBLE,
        "Gói khám hoặc dịch vụ không phù hợp độ tuổi hoặc giới tính");
  }

  private static ConflictException profileRequired() {
    return new ConflictException(
        BookingErrorCode.PATIENT_PROFILE_REQUIRED, "Cần hồ sơ bệnh nhân để đặt lịch");
  }

  private static NotFoundException notFound() {
    return new NotFoundException(
        ReservationErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn");
  }
}
