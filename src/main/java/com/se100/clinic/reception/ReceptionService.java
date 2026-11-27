package com.se100.clinic.reception;

import com.se100.clinic.appointment.AppointmentBookingService;
import com.se100.clinic.appointment.BookingDtos.ReceptionReference;
import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.facility.enums.FacilityStatus;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.PatientActor;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.PatientMedicalHistoryService;
import com.se100.clinic.patient.PatientService;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.reception.ReceptionDtos.ClinicRoomOption;
import com.se100.clinic.reception.ReceptionDtos.CreateReceptionRequest;
import com.se100.clinic.reception.ReceptionDtos.ItemRoomAssignment;
import com.se100.clinic.reception.ReceptionDtos.PlannedOrderItem;
import com.se100.clinic.reception.ReceptionDtos.ReceptionAppointmentItem;
import com.se100.clinic.reception.ReceptionDtos.ReceptionListItem;
import com.se100.clinic.reception.ReceptionDtos.ReceptionLookupResponse;
import com.se100.clinic.reception.ReceptionDtos.ReceptionPatientCard;
import com.se100.clinic.reception.ReceptionDtos.ReceptionTicketResponse;
import com.se100.clinic.reception.ReceptionDtos.RoutedOrderResponse;
import com.se100.clinic.reception.ReceptionDtos.StaffClinicResponse;
import com.se100.clinic.reception.ReceptionDtos.UpdateOrderRoutingRequest;
import com.se100.clinic.reception.ReceptionRepository.AppointmentDetailRow;
import com.se100.clinic.reception.ReceptionRepository.AppointmentLookupRef;
import com.se100.clinic.reception.ReceptionRepository.CatalogItemRow;
import com.se100.clinic.reception.ReceptionRepository.LockedAppointmentRow;
import com.se100.clinic.reception.ReceptionRepository.LockedOrderRow;
import com.se100.clinic.reception.ReceptionRepository.RoomRow;
import com.se100.clinic.reception.ReceptionRepository.StaffClinicRow;
import com.se100.clinic.reception.ReceptionRepository.VisitHeaderRow;
import com.se100.clinic.reception.enums.CardAlertLevel;
import com.se100.clinic.reception.enums.PaymentSummaryStatus;
import com.se100.clinic.reception.enums.PunctualityStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.FieldViolation;
import com.se100.clinic.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>PUBLIC</b> — Patient reception and room-routing workflow (REC-01..REC-04).
 *
 * <p>Business rules:
 *
 * <ul>
 *   <li>Every operation enforces clinic scope: a receptionist assigned to Clinic A cannot look up
 *       or check in appointments belonging to Clinic B ({@code 403 CLINIC_ACCESS_DENIED}).
 *   <li>Patient search, medical history and card status are delegated to {@link PatientService} and
 *       {@link PatientMedicalHistoryService}; QR lookup uses {@link AppointmentBookingService}.
 *   <li>Expired or expiring-soon cards display warnings but still allow check-in; revoked cards are
 *       blocked ({@code 409 PATIENT_CARD_REVOKED}).
 *   <li>Appointments are marked {@code EARLY} (&gt;30 min early), {@code ON_TIME}, {@code LATE}
 *       (after {@code startAt}) or {@code NOT_TODAY}. Same-day {@code CONFIRMED} appointments are
 *       receivable until {@code shiftEndAt}.
 *   <li>Double check-in and concurrent requests are serialized via {@code SELECT ... FOR UPDATE}
 *       plus the unique DB constraint on {@code medical_visits.appointment_id}.
 *   <li>Multi-occurrence items (e.g. 3-dose HPV vaccine) generate all occurrences ({@code 1..N})
 *       but route only occurrence #1 into today's functional room queue.
 * </ul>
 */
@Service
public class ReceptionService {

  public static final Set<String> SORTABLE_FIELDS = Set.of("receivedAt", "code", "status");
  public static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("receivedAt"));

  static final long EARLY_THRESHOLD_MINUTES = 30L;
  private static final int MAX_PATIENT_SEARCH_RESULTS = 20;
  private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private static final List<String> LAB_KEYWORDS =
      List.of("lab", "xn", "xet nghiem", "mau", "sinh hoa", "huyet hoc", "vi sinh");
  private static final List<String> IMAGING_KEYWORDS =
      List.of(
          "img",
          "cdha",
          "chan doan",
          "x-quang",
          "xquang",
          "xray",
          "sieu am",
          "ct",
          "mri",
          "ecg",
          "dien tim",
          "noi soi",
          "dxa",
          "inbody");
  private static final List<String> VACCINATION_KEYWORDS =
      List.of("vac", "tiem", "vaccine", "vac-xin", "vacxin", "chung ngua");

  private final ReceptionRepository repository;
  private final PatientService patientService;
  private final PatientMedicalHistoryService medicalHistoryService;
  private final AppointmentBookingService bookingService;
  private final Clock clock;

  public ReceptionService(
      ReceptionRepository repository,
      PatientService patientService,
      PatientMedicalHistoryService medicalHistoryService,
      AppointmentBookingService bookingService,
      Clock clock) {
    this.repository = repository;
    this.patientService = patientService;
    this.medicalHistoryService = medicalHistoryService;
    this.bookingService = bookingService;
    this.clock = clock;
  }

  /** Lists clinics assigned to the current staff member, together with their open rooms. */
  @Transactional(readOnly = true)
  public List<StaffClinicResponse> staffClinics(Long staffId) {
    return repository.findStaffClinics(staffId).stream()
        .map(
            clinic ->
                new StaffClinicResponse(
                    clinic.id(),
                    clinic.publicId(),
                    clinic.code(),
                    clinic.name(),
                    clinic.status(),
                    repository.findOpenRoomsByClinic(clinic.id())))
        .toList();
  }

  /**
   * Counter lookup by QR payload, appointment code, phone number, or identity document (REC-01 &
   * REC-02).
   */
  @Transactional(readOnly = true)
  public ReceptionLookupResponse lookup(
      Long userId,
      Long staffId,
      Long requestedClinicId,
      String qr,
      String appointmentCode,
      String phone,
      String identityNumber) {
    String cleanQr = cleanOrNull(qr);
    String cleanCode = cleanOrNull(appointmentCode);
    String cleanPhone = cleanOrNull(phone);
    String cleanIdentity = cleanOrNull(identityNumber);

    int providedCount =
        (cleanQr != null ? 1 : 0)
            + (cleanCode != null ? 1 : 0)
            + (cleanPhone != null ? 1 : 0)
            + (cleanIdentity != null ? 1 : 0);
    if (providedCount != 1) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Tham số tra cứu không hợp lệ",
          List.of(
              new FieldViolation(
                  "lookup",
                  "Vui lòng truyền đúng một trong bốn tham số: qr, appointmentCode, phone hoặc"
                      + " identityNumber")));
    }

    StaffClinicRow clinic = resolveClinicScope(staffId, requestedClinicId);
    PatientActor actor = new PatientActor(userId, UserRole.MEDICAL_STAFF);
    List<ClinicRoomOption> openRooms = repository.findOpenRoomsByClinic(clinic.id());
    Instant now = clock.instant();
    LocalDate today = today();
    Map<Long, Integer> queueLoads =
        repository.activeQueueCountsByRoom(
            clinic.id(), startOfDay(today), startOfDay(today.plusDays(1)));

    List<PatientResponse> matchedPatients;
    if (cleanQr != null || cleanCode != null) {
      UUID patientPublicId = resolvePatientFromBookingLookup(clinic.id(), cleanQr, cleanCode);
      matchedPatients = List.of(patientService.getProfile(patientPublicId, actor));
    } else if (cleanPhone != null) {
      matchedPatients =
          patientService
              .searchByPhone(
                  cleanPhone,
                  actor,
                  PageRequest.of(0, MAX_PATIENT_SEARCH_RESULTS, PatientService.DEFAULT_SORT))
              .getContent();
      if (matchedPatients.isEmpty()) {
        throw new NotFoundException(
            CommonErrorCode.NOT_FOUND,
            "Không tìm thấy bệnh nhân có số điện thoại khớp thông tin tra cứu");
      }
    } else {
      matchedPatients =
          patientService
              .searchByIdentityNumber(
                  cleanIdentity,
                  actor,
                  PageRequest.of(0, MAX_PATIENT_SEARCH_RESULTS, PatientService.DEFAULT_SORT))
              .getContent();
      if (matchedPatients.isEmpty()) {
        throw new NotFoundException(
            CommonErrorCode.NOT_FOUND,
            "Không tìm thấy bệnh nhân có số giấy tờ (CCCD/hộ chiếu) khớp thông tin tra cứu");
      }
    }

    List<ReceptionPatientCard> cards =
        matchedPatients.stream()
            .map(
                profile ->
                    buildPatientCard(
                        profile, actor, clinic.id(), openRooms, queueLoads, now, today))
            .toList();

    return new ReceptionLookupResponse(
        clinic.id(), clinic.publicId(), clinic.code(), clinic.name(), openRooms, cards);
  }

  /**
   * Validates eligibility, marks appointment CHECKED_IN, creates the reception ticket ({@code
   * medical_visits}), generates all order occurrences, and routes current-visit items into
   * clinical/paraclinical rooms (REC-03 & REC-04).
   */
  @Transactional
  public ReceptionTicketResponse checkIn(
      Long userId, Long staffId, CreateReceptionRequest request) {
    List<StaffClinicRow> assignedClinics = requireAssignedClinics(staffId);
    if (request.clinicId() != null) {
      ensureStaffAssignedToClinic(assignedClinics, request.clinicId());
    }

    LockedAppointmentRow locked =
        repository
            .lockAppointmentForCheckIn(request.appointmentPublicId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn"));

    if (request.clinicId() != null && !request.clinicId().equals(locked.clinicId())) {
      throw clinicAccessDenied();
    }
    ensureStaffAssignedToClinic(assignedClinics, locked.clinicId());

    if (locked.existingVisitId() != null || locked.status() == AppointmentStatus.CHECKED_IN) {
      throw alreadyCheckedIn();
    }
    if (locked.status() != AppointmentStatus.CONFIRMED) {
      throw new ConflictException(
          ReceptionErrorCode.APPOINTMENT_NOT_RECEIVABLE,
          "Chỉ lịch hẹn ở trạng thái đã xác nhận (CONFIRMED) mới được tiếp nhận");
    }

    Instant now = clock.instant();
    LocalDate today = today();
    if (!locked.workDate().equals(today)) {
      throw new ConflictException(
          ReceptionErrorCode.APPOINTMENT_NOT_TODAY,
          "Lịch hẹn không thuộc ngày hôm nay nên không thể tiếp nhận");
    }
    if (!now.isBefore(locked.shiftEndAt())) {
      throw new ConflictException(
          ReceptionErrorCode.SHIFT_ALREADY_ENDED,
          "Ca khám của lịch hẹn đã kết thúc, không thể tiếp nhận");
    }
    if (!FacilityStatus.OPENING.name().equals(locked.clinicStatus())
        || !FacilityStatus.OPENING.name().equals(locked.roomStatus())) {
      throw new ConflictException(
          ReceptionErrorCode.CLINIC_NOT_OPEN, "Cơ sở hoặc phòng khám của ca đang ngừng hoạt động");
    }

    PatientActor actor = new PatientActor(userId, UserRole.MEDICAL_STAFF);
    CardCheck cardCheck = evaluateCard(locked.patientPublicId(), actor);
    if (cardCheck.alert() == CardAlertLevel.REVOKED) {
      throw new ConflictException(
          ReceptionErrorCode.PATIENT_CARD_REVOKED,
          "Thẻ bệnh nhân đã bị thu hồi, cần mở lại thẻ trước khi tiếp nhận");
    }

    Map<UUID, RoomRow> manualRoomOverrides =
        validateRoomAssignments(request.roomAssignments(), locked.clinicId());

    List<CatalogItemRow> catalogItems =
        loadCatalogItems(locked.packageVersionId(), locked.serviceId());
    MedicalOrderSource orderSource =
        locked.packageVersionId() != null ? MedicalOrderSource.PACKAGE : MedicalOrderSource.SERVICE;

    UUID visitPublicId = UUID.randomUUID();
    String visitCode = generateCode("PTN", today);

    Long visitId;
    try {
      repository.markAppointmentCheckedIn(
          locked.appointmentId(),
          staffId,
          locked.scheduleId(),
          locked.startAt(),
          locked.endAt(),
          now);
      visitId =
          repository.insertVisit(
              visitPublicId, visitCode, locked.patientId(), locked.appointmentId(), staffId, now);
    } catch (DataIntegrityViolationException e) {
      if (DbErrors.isUniqueViolation(e)) {
        throw alreadyCheckedIn();
      }
      throw e;
    }

    List<ClinicRoomOption> openRooms = repository.findOpenRoomsByClinic(locked.clinicId());
    Instant dayStart = startOfDay(today);
    Instant dayEnd = startOfDay(today.plusDays(1));
    Map<Long, Integer> queueLoads =
        new HashMap<>(repository.activeQueueCountsByRoom(locked.clinicId(), dayStart, dayEnd));

    for (CatalogItemRow item : catalogItems) {
      int totalOccurrences = Math.max(1, item.quantity());
      for (int occurrenceNo = 1; occurrenceNo <= totalOccurrences; occurrenceNo++) {
        boolean isCurrentVisit = occurrenceNo == 1;
        Long assignedRoomId = null;
        Long performedVisitId = null;
        MedicalOrderStatus orderStatus = MedicalOrderStatus.ORDERED;
        Integer queueNumber = null;
        Instant queuedAt = null;

        if (isCurrentVisit) {
          performedVisitId = visitId;
          assignedRoomId =
              resolveTargetRoomId(
                  item, locked.roomId(), openRooms, queueLoads, manualRoomOverrides);
          if (assignedRoomId != null) {
            queueNumber = repository.allocateQueueNumberForRoom(assignedRoomId, dayStart, dayEnd);
            queuedAt = now;
            orderStatus = MedicalOrderStatus.WAITING;
            queueLoads.merge(assignedRoomId, 1, Integer::sum);
          }
        }

        repository.insertOrder(
            UUID.randomUUID(),
            generateCode("ORD", today),
            visitId,
            item.itemId(),
            orderSource,
            locked.packageVersionId(),
            locked.serviceId(),
            occurrenceNo,
            assignedRoomId,
            performedVisitId,
            item.itemName(),
            item.instruction(),
            item.unitPrice(),
            orderStatus,
            queueNumber,
            queuedAt,
            now);
      }
    }

    String staffName = repository.findStaffName(staffId).orElse("Nhân viên y tế");
    PunctualityEvaluation punctuality =
        evaluatePunctuality(locked.workDate(), locked.startAt(), now, today);

    return new ReceptionTicketResponse(
        visitPublicId,
        visitCode,
        MedicalVisitStatus.WAITING,
        now,
        staffId,
        staffName,
        locked.clinicId(),
        locked.clinicName(),
        locked.appointmentPublicId(),
        locked.code(),
        AppointmentStatus.CHECKED_IN,
        locked.startAt(),
        locked.endAt(),
        punctuality.status(),
        punctuality.minutesDelta(),
        locked.patientPublicId(),
        locked.patientName(),
        locked.patientPhone(),
        cardCheck.alert(),
        cardCheck.warningMessage(),
        locked.doctorName(),
        locked.shiftName(),
        locked.roomName(),
        locked.packageName(),
        locked.serviceName(),
        repository.findOrdersByVisitId(visitId));
  }

  /** Lists reception tickets for the staff's clinic (defaults to today in app timezone). */
  @Transactional(readOnly = true)
  public Page<ReceptionListItem> list(
      Long staffId,
      Long requestedClinicId,
      LocalDate date,
      String keyword,
      MedicalVisitStatus status,
      Pageable pageable) {
    StaffClinicRow clinic = resolveClinicScope(staffId, requestedClinicId);
    LocalDate effectiveDate = date != null ? date : today();
    return repository.listReceptions(clinic.id(), effectiveDate, keyword, status, pageable);
  }

  /** Loads one reception ticket and its routed orders; enforces clinic scope. */
  @Transactional(readOnly = true)
  public ReceptionTicketResponse getTicket(Long userId, Long staffId, UUID visitPublicId) {
    List<StaffClinicRow> assignedClinics = requireAssignedClinics(staffId);
    VisitHeaderRow header =
        repository
            .findVisitHeader(visitPublicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.RECEPTION_VISIT_NOT_FOUND,
                        "Không tìm thấy phiếu tiếp nhận"));
    ensureStaffAssignedToClinic(assignedClinics, header.clinicId());

    PatientActor actor = new PatientActor(userId, UserRole.MEDICAL_STAFF);
    CardCheck cardCheck = evaluateCard(header.patientPublicId(), actor);
    LocalDate visitDate = header.receivedAt().atZone(clock.getZone()).toLocalDate();
    PunctualityEvaluation punctuality =
        evaluatePunctuality(
            header.workDate(), header.appointmentStartAt(), header.receivedAt(), visitDate);

    return new ReceptionTicketResponse(
        header.visitPublicId(),
        header.visitCode(),
        header.visitStatus(),
        header.receivedAt(),
        header.receivedByStaffId(),
        header.receivedByStaffName(),
        header.clinicId(),
        header.clinicName(),
        header.appointmentPublicId(),
        header.appointmentCode(),
        header.appointmentStatus(),
        header.appointmentStartAt(),
        header.appointmentEndAt(),
        punctuality.status(),
        punctuality.minutesDelta(),
        header.patientPublicId(),
        header.patientName(),
        header.patientPhone(),
        cardCheck.alert(),
        cardCheck.warningMessage(),
        header.doctorName(),
        header.shiftName(),
        header.clinicalRoomName(),
        header.packageName(),
        header.serviceName(),
        repository.findOrdersByVisitId(header.visitId()));
  }

  /**
   * Assigns or re-routes an order of a reception visit to an open functional room in the same
   * clinic (REC-04).
   */
  @Transactional
  public RoutedOrderResponse routeOrder(
      Long staffId, UUID visitPublicId, UUID orderPublicId, UpdateOrderRoutingRequest request) {
    List<StaffClinicRow> assignedClinics = requireAssignedClinics(staffId);
    LockedOrderRow order =
        repository
            .lockOrderForRouting(visitPublicId, orderPublicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.RECEPTION_ORDER_NOT_FOUND,
                        "Không tìm thấy hạng mục chỉ định thuộc phiếu tiếp nhận này"));
    ensureStaffAssignedToClinic(assignedClinics, order.clinicId());

    if (order.visitStatus() == MedicalVisitStatus.COMPLETED
        || order.visitStatus() == MedicalVisitStatus.CANCELLED) {
      throw new ConflictException(
          ReceptionErrorCode.RECEPTION_VISIT_CLOSED,
          "Phiếu tiếp nhận đã đóng, không thể điều phối phòng");
    }
    if (order.orderStatus() == MedicalOrderStatus.COMPLETED
        || order.orderStatus() == MedicalOrderStatus.CANCELLED
        || order.orderStatus() == MedicalOrderStatus.IN_PROGRESS) {
      throw new ConflictException(
          ReceptionErrorCode.ORDER_NOT_ROUTABLE,
          "Hạng mục đang thực hiện hoặc đã kết thúc, không thể đổi phòng");
    }

    RoomRow room =
        repository
            .findRoomByPublicId(request.roomPublicId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.RECEPTION_ROOM_NOT_FOUND,
                        "Không tìm thấy phòng chức năng"));
    if (!room.clinicId().equals(order.clinicId())) {
      throw new ConflictException(
          ReceptionErrorCode.ROOM_NOT_IN_CLINIC, "Phòng điều phối không thuộc cơ sở tiếp nhận");
    }
    if (!FacilityStatus.OPENING.name().equals(room.status())) {
      throw new ConflictException(
          ReceptionErrorCode.ROOM_NOT_OPEN, "Phòng điều phối đang ngừng hoạt động");
    }

    Instant now = clock.instant();
    LocalDate today = today();
    int queueNumber =
        repository.allocateQueueNumberForRoom(
            room.id(), startOfDay(today), startOfDay(today.plusDays(1)));
    repository.updateOrderRouting(order.orderId(), order.visitId(), room.id(), queueNumber, now);

    return repository
        .findOrderByPublicId(orderPublicId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    ReceptionErrorCode.RECEPTION_ORDER_NOT_FOUND,
                    "Không tìm thấy hạng mục chỉ định sau khi điều phối"));
  }

  // ---- lookup & eligibility helpers ---------------------------------------------------------

  private UUID resolvePatientFromBookingLookup(Long clinicId, String qr, String appointmentCode) {
    if (qr != null) {
      Optional<ReceptionReference> verified = bookingService.findByQr(qr, clinicId);
      if (verified.isPresent()) {
        return repository
            .findPatientPublicIdById(verified.get().patientId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.APPOINTMENT_NOT_FOUND,
                        "Không tìm thấy lịch hẹn tương ứng với mã QR"));
      }
      Optional<AppointmentLookupRef> anyClinicMatch =
          repository.findAppointmentRefByCodeOrQrHash(qr, sha256Hex(qr));
      if (anyClinicMatch.isPresent()) {
        if (!anyClinicMatch.get().clinicId().equals(clinicId)) {
          throw clinicAccessDenied();
        }
        return anyClinicMatch.get().patientPublicId();
      }
      throw new NotFoundException(
          ReceptionErrorCode.APPOINTMENT_NOT_FOUND, "Mã QR không hợp lệ hoặc không tồn tại");
    }

    AppointmentLookupRef byCode =
        repository
            .findAppointmentRefByCodeOrQrHash(appointmentCode, sha256Hex(appointmentCode))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ReceptionErrorCode.APPOINTMENT_NOT_FOUND,
                        "Không tìm thấy lịch hẹn với mã đặt lịch này"));
    if (!byCode.clinicId().equals(clinicId)) {
      throw clinicAccessDenied();
    }
    return byCode.patientPublicId();
  }

  private ReceptionPatientCard buildPatientCard(
      PatientResponse profile,
      PatientActor actor,
      Long staffClinicId,
      List<ClinicRoomOption> openRooms,
      Map<Long, Integer> queueLoads,
      Instant now,
      LocalDate today) {
    MedicalHistorySummaryResponse history =
        medicalHistoryService.getSummary(profile.publicId(), actor);
    CardCheck cardCheck = evaluateCard(profile.publicId(), actor);

    List<AppointmentDetailRow> rows = repository.findAppointmentsForPatient(profile.publicId());
    List<ReceptionAppointmentItem> items =
        rows.stream()
            .sorted(nearestAppointmentComparator(now, today))
            .map(
                row ->
                    toAppointmentItem(
                        row, staffClinicId, cardCheck.alert(), openRooms, queueLoads, now, today))
            .toList();

    return new ReceptionPatientCard(
        profile,
        history,
        cardCheck.validity(),
        cardCheck.alert(),
        cardCheck.warningMessage(),
        items);
  }

  private ReceptionAppointmentItem toAppointmentItem(
      AppointmentDetailRow row,
      Long staffClinicId,
      CardAlertLevel cardAlert,
      List<ClinicRoomOption> openRooms,
      Map<Long, Integer> queueLoads,
      Instant now,
      LocalDate today) {
    PunctualityEvaluation punctuality =
        evaluatePunctuality(row.workDate(), row.startAt(), now, today);
    PaymentSummaryStatus paymentStatus =
        evaluatePaymentStatus(row.depositRequired(), row.paidAmount());

    Eligibility eligibility = evaluateEligibility(row, staffClinicId, cardAlert, now, today);

    List<PlannedOrderItem> plannedItems =
        row.clinicId().equals(staffClinicId)
            ? buildPlannedItems(row, openRooms, queueLoads)
            : List.of();

    return new ReceptionAppointmentItem(
        row.appointmentPublicId(),
        row.code(),
        row.status(),
        row.startAt(),
        row.endAt(),
        row.workDate(),
        row.shiftCode(),
        row.shiftName(),
        row.shiftStartAt(),
        row.shiftEndAt(),
        row.clinicId(),
        row.clinicName(),
        row.roomId(),
        row.roomPublicId(),
        row.roomCode(),
        row.roomName(),
        row.doctorId(),
        row.doctorName(),
        row.specialtyName(),
        row.packageName(),
        row.packageVersion(),
        row.serviceName(),
        row.priceSnapshot(),
        row.depositRequired(),
        row.paidAmount(),
        paymentStatus,
        punctuality.status(),
        punctuality.minutesDelta(),
        eligibility.receivable(),
        eligibility.code(),
        eligibility.reason(),
        row.existingVisitPublicId(),
        row.existingVisitCode(),
        row.existingVisitReceivedAt(),
        plannedItems);
  }

  private List<PlannedOrderItem> buildPlannedItems(
      AppointmentDetailRow row, List<ClinicRoomOption> openRooms, Map<Long, Integer> queueLoads) {
    List<CatalogItemRow> items = loadCatalogItems(row.packageVersionId(), row.serviceId());
    List<PlannedOrderItem> planned = new ArrayList<>();
    for (CatalogItemRow item : items) {
      ClinicRoomOption suggested =
          suggestRoom(item.itemType(), row.roomId(), openRooms, queueLoads).orElse(null);
      planned.add(
          new PlannedOrderItem(
              item.itemId(),
              item.itemPublicId(),
              item.itemName(),
              item.itemType(),
              Math.max(1, item.quantity()),
              suggested == null ? null : suggested.roomPublicId(),
              suggested == null ? null : suggested.code(),
              suggested == null ? null : suggested.name()));
    }
    return planned;
  }

  private List<CatalogItemRow> loadCatalogItems(Long packageVersionId, Long serviceId) {
    if (packageVersionId != null) {
      return repository.findPackageVersionItems(packageVersionId);
    }
    if (serviceId != null) {
      return repository.findServiceItems(serviceId);
    }
    return List.of();
  }

  private record Eligibility(boolean receivable, String code, String reason) {}

  private static Eligibility evaluateEligibility(
      AppointmentDetailRow row,
      Long staffClinicId,
      CardAlertLevel cardAlert,
      Instant now,
      LocalDate today) {
    if (!row.clinicId().equals(staffClinicId)) {
      return new Eligibility(
          false,
          ReceptionErrorCode.CLINIC_ACCESS_DENIED.name(),
          "Lịch hẹn thuộc cơ sở khác ("
              + row.clinicName()
              + "), nhân viên không có quyền tiếp nhận");
    }
    if (row.existingVisitPublicId() != null || row.status() == AppointmentStatus.CHECKED_IN) {
      return new Eligibility(
          false,
          ReceptionErrorCode.APPOINTMENT_ALREADY_CHECKED_IN.name(),
          "Lịch hẹn đã được tiếp nhận");
    }
    if (row.status() != AppointmentStatus.CONFIRMED) {
      return new Eligibility(
          false,
          ReceptionErrorCode.APPOINTMENT_NOT_RECEIVABLE.name(),
          "Trạng thái lịch hẹn (" + row.status() + ") không cho phép tiếp nhận");
    }
    if (!row.workDate().equals(today)) {
      return new Eligibility(
          false,
          ReceptionErrorCode.APPOINTMENT_NOT_TODAY.name(),
          "Lịch hẹn thuộc ngày " + row.workDate() + ", không phải hôm nay");
    }
    if (!now.isBefore(row.shiftEndAt())) {
      return new Eligibility(
          false, ReceptionErrorCode.SHIFT_ALREADY_ENDED.name(), "Ca khám của lịch hẹn đã kết thúc");
    }
    if (!FacilityStatus.OPENING.name().equals(row.clinicStatus())
        || !FacilityStatus.OPENING.name().equals(row.roomStatus())) {
      return new Eligibility(
          false,
          ReceptionErrorCode.CLINIC_NOT_OPEN.name(),
          "Cơ sở hoặc phòng khám của ca đang ngừng hoạt động");
    }
    if (cardAlert == CardAlertLevel.REVOKED) {
      return new Eligibility(
          false,
          ReceptionErrorCode.PATIENT_CARD_REVOKED.name(),
          "Thẻ bệnh nhân đã bị thu hồi, cần mở lại thẻ trước khi tiếp nhận");
    }
    return new Eligibility(true, null, null);
  }

  private record CardCheck(
      PatientCardValidityResponse validity, CardAlertLevel alert, String warningMessage) {}

  private CardCheck evaluateCard(UUID patientPublicId, PatientActor actor) {
    try {
      PatientCardValidityResponse validity =
          patientService.checkCardValidity(patientPublicId, actor);
      PatientCardStatus status = validity.status();
      return switch (status) {
        case ACTIVE -> new CardCheck(validity, CardAlertLevel.NONE, null);
        case EXPIRING_SOON ->
            new CardCheck(
                validity,
                CardAlertLevel.EXPIRING_SOON,
                "Thẻ bệnh nhân sắp hết hạn (còn "
                    + validity.daysRemaining()
                    + " ngày). Nhắc bệnh nhân gia hạn thẻ.");
        case EXPIRED ->
            new CardCheck(
                validity,
                CardAlertLevel.EXPIRED,
                "Thẻ bệnh nhân đã hết hạn. Vẫn có thể tiếp nhận nhưng cần hỗ trợ gia hạn thẻ tại"
                    + " quầy.");
        case REVOKED ->
            new CardCheck(
                validity,
                CardAlertLevel.REVOKED,
                "Thẻ bệnh nhân đã bị thu hồi, không thể tiếp nhận cho đến khi mở lại thẻ.");
      };
    } catch (NotFoundException missingCard) {
      return new CardCheck(null, CardAlertLevel.MISSING, "Bệnh nhân chưa được cấp thẻ điện tử.");
    }
  }

  private record PunctualityEvaluation(PunctualityStatus status, long minutesDelta) {}

  static PunctualityEvaluation evaluatePunctuality(
      LocalDate workDate, Instant appointmentStartAt, Instant now, LocalDate referenceDay) {
    long minutesDelta = Duration.between(appointmentStartAt, now).toMinutes();
    if (!workDate.equals(referenceDay)) {
      return new PunctualityEvaluation(PunctualityStatus.NOT_TODAY, minutesDelta);
    }
    if (now.isAfter(appointmentStartAt)) {
      return new PunctualityEvaluation(PunctualityStatus.LATE, Math.max(1L, minutesDelta));
    }
    long minutesEarly = Duration.between(now, appointmentStartAt).toMinutes();
    if (minutesEarly > EARLY_THRESHOLD_MINUTES) {
      return new PunctualityEvaluation(PunctualityStatus.EARLY, -minutesEarly);
    }
    return new PunctualityEvaluation(PunctualityStatus.ON_TIME, minutesDelta);
  }

  static PaymentSummaryStatus evaluatePaymentStatus(
      BigDecimal depositRequired, BigDecimal paidAmount) {
    BigDecimal required = depositRequired == null ? BigDecimal.ZERO : depositRequired;
    BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
    if (required.compareTo(BigDecimal.ZERO) <= 0) {
      return paid.compareTo(BigDecimal.ZERO) > 0
          ? PaymentSummaryStatus.PAID
          : PaymentSummaryStatus.NOT_REQUIRED;
    }
    if (paid.compareTo(BigDecimal.ZERO) <= 0) {
      return PaymentSummaryStatus.UNPAID;
    }
    if (paid.compareTo(required) >= 0) {
      return PaymentSummaryStatus.PAID;
    }
    return PaymentSummaryStatus.PARTIALLY_PAID;
  }

  /**
   * Nearest-first ordering: today's active appointments first (earliest/closest start time first),
   * then future appointments ascending, then past/closed appointments most-recent first.
   */
  private static Comparator<AppointmentDetailRow> nearestAppointmentComparator(
      Instant now, LocalDate today) {
    return Comparator.<AppointmentDetailRow>comparingInt(row -> sortBucket(row, today))
        .thenComparingLong(row -> Math.abs(Duration.between(now, row.startAt()).toSeconds()))
        .thenComparing(AppointmentDetailRow::startAt)
        .thenComparing(AppointmentDetailRow::appointmentId);
  }

  private static int sortBucket(AppointmentDetailRow row, LocalDate today) {
    boolean active =
        row.status() == AppointmentStatus.CONFIRMED || row.status() == AppointmentStatus.CHECKED_IN;
    if (row.workDate().equals(today) && active) {
      return 0;
    }
    if (row.workDate().isAfter(today) && active) {
      return 1;
    }
    if (row.workDate().equals(today)) {
      return 2;
    }
    return 3;
  }

  // ---- room routing helpers (REC-04) --------------------------------------------------------

  private Map<UUID, RoomRow> validateRoomAssignments(
      List<ItemRoomAssignment> assignments, Long clinicId) {
    Map<UUID, RoomRow> resolved = new HashMap<>();
    if (assignments == null || assignments.isEmpty()) {
      return resolved;
    }
    for (ItemRoomAssignment assignment : assignments) {
      RoomRow room =
          repository
              .findRoomByPublicId(assignment.roomPublicId())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          ReceptionErrorCode.RECEPTION_ROOM_NOT_FOUND,
                          "Không tìm thấy phòng chức năng được chọn"));
      if (!room.clinicId().equals(clinicId)) {
        throw new ConflictException(
            ReceptionErrorCode.ROOM_NOT_IN_CLINIC, "Phòng điều phối không thuộc cơ sở tiếp nhận");
      }
      if (!FacilityStatus.OPENING.name().equals(room.status())) {
        throw new ConflictException(
            ReceptionErrorCode.ROOM_NOT_OPEN, "Phòng điều phối đang ngừng hoạt động");
      }
      resolved.put(assignment.itemPublicId(), room);
    }
    return resolved;
  }

  private Long resolveTargetRoomId(
      CatalogItemRow item,
      Long clinicalRoomId,
      List<ClinicRoomOption> openRooms,
      Map<Long, Integer> queueLoads,
      Map<UUID, RoomRow> manualOverrides) {
    RoomRow override = manualOverrides.get(item.itemPublicId());
    if (override != null) {
      return override.id();
    }
    return suggestRoom(item.itemType(), clinicalRoomId, openRooms, queueLoads)
        .map(ClinicRoomOption::roomId)
        .orElse(null);
  }

  static Optional<ClinicRoomOption> suggestRoom(
      MedicalItemType itemType,
      Long clinicalRoomId,
      List<ClinicRoomOption> openRooms,
      Map<Long, Integer> queueLoads) {
    if (itemType == MedicalItemType.CLINICAL) {
      return openRooms.stream().filter(r -> r.roomId().equals(clinicalRoomId)).findFirst();
    }
    List<String> keywords =
        switch (itemType) {
          case LAB -> LAB_KEYWORDS;
          case IMAGING -> IMAGING_KEYWORDS;
          case VACCINATION -> VACCINATION_KEYWORDS;
          default -> List.of();
        };
    if (!keywords.isEmpty()) {
      Optional<ClinicRoomOption> keywordMatch =
          openRooms.stream()
              .filter(room -> matchesKeywords(room, keywords))
              .min(
                  Comparator.comparingInt(
                          (ClinicRoomOption r) -> queueLoads.getOrDefault(r.roomId(), 0))
                      .thenComparing(ClinicRoomOption::code));
      if (keywordMatch.isPresent()) {
        return keywordMatch;
      }
    }
    // Fallback: pick another open functional room in the same clinic (least queued), if available.
    return openRooms.stream()
        .filter(room -> !room.roomId().equals(clinicalRoomId))
        .min(
            Comparator.comparingInt((ClinicRoomOption r) -> queueLoads.getOrDefault(r.roomId(), 0))
                .thenComparing(ClinicRoomOption::code));
  }

  private static boolean matchesKeywords(ClinicRoomOption room, List<String> keywords) {
    String haystack = normalizeForMatch(room.code() + " " + room.name());
    for (String keyword : keywords) {
      if (haystack.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeForMatch(String input) {
    String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
    return decomposed
        .replaceAll("\\p{M}+", "")
        .replace('đ', 'd')
        .replace('Đ', 'd')
        .toLowerCase(Locale.ROOT);
  }

  // ---- clinic scope & utility helpers -------------------------------------------------------

  private List<StaffClinicRow> requireAssignedClinics(Long staffId) {
    List<StaffClinicRow> clinics = repository.findStaffClinics(staffId);
    if (clinics.isEmpty()) {
      throw clinicAccessDenied();
    }
    return clinics;
  }

  private StaffClinicRow resolveClinicScope(Long staffId, Long requestedClinicId) {
    List<StaffClinicRow> clinics = requireAssignedClinics(staffId);
    if (requestedClinicId != null) {
      return clinics.stream()
          .filter(c -> c.id().equals(requestedClinicId))
          .findFirst()
          .orElseThrow(ReceptionService::clinicAccessDenied);
    }
    if (clinics.size() == 1) {
      return clinics.getFirst();
    }
    throw new BusinessException(
        ReceptionErrorCode.STAFF_CLINIC_REQUIRED,
        "Nhân viên được phân công nhiều cơ sở, vui lòng chọn clinicId");
  }

  private static void ensureStaffAssignedToClinic(
      List<StaffClinicRow> assignedClinics, Long clinicId) {
    boolean allowed = assignedClinics.stream().anyMatch(c -> c.id().equals(clinicId));
    if (!allowed) {
      throw clinicAccessDenied();
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private Instant startOfDay(LocalDate date) {
    ZoneId zone = clock.getZone();
    return date.atStartOfDay(zone).toInstant();
  }

  private static String generateCode(String prefix, LocalDate date) {
    String random =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return prefix + "-" + date.format(CODE_DATE_FORMAT) + "-" + random;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String cleanOrNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static BusinessException clinicAccessDenied() {
    return new BusinessException(
        ReceptionErrorCode.CLINIC_ACCESS_DENIED,
        "Nhân viên không có quyền tiếp nhận tại cơ sở này");
  }

  private static ConflictException alreadyCheckedIn() {
    return new ConflictException(
        ReceptionErrorCode.APPOINTMENT_ALREADY_CHECKED_IN,
        "Lịch hẹn này đã được tiếp nhận, không thể tạo phiếu trùng");
  }
}
