package com.se100.clinic.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.appointment.AppointmentBookingService;
import com.se100.clinic.appointment.BookingDtos.ReceptionReference;
import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.PatientActor;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.PatientMedicalHistoryService;
import com.se100.clinic.patient.PatientService;
import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.IdentityType;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.reception.ReceptionDtos.ClinicRoomOption;
import com.se100.clinic.reception.ReceptionDtos.CreateReceptionRequest;
import com.se100.clinic.reception.ReceptionDtos.ItemRoomAssignment;
import com.se100.clinic.reception.ReceptionDtos.UpdateOrderRoutingRequest;
import com.se100.clinic.reception.ReceptionRepository.AppointmentDetailRow;
import com.se100.clinic.reception.ReceptionRepository.AppointmentLookupRef;
import com.se100.clinic.reception.ReceptionRepository.CatalogItemRow;
import com.se100.clinic.reception.ReceptionRepository.LockedAppointmentRow;
import com.se100.clinic.reception.ReceptionRepository.LockedOrderRow;
import com.se100.clinic.reception.ReceptionRepository.RoomRow;
import com.se100.clinic.reception.ReceptionRepository.StaffClinicRow;
import com.se100.clinic.reception.enums.CardAlertLevel;
import com.se100.clinic.reception.enums.PaymentSummaryStatus;
import com.se100.clinic.reception.enums.PunctualityStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class ReceptionServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);
  // 2026-11-27 09:00 Asia/Ho_Chi_Minh = 2026-11-27T02:00:00Z
  private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");
  private static final Instant SHIFT_START = Instant.parse("2026-11-27T01:00:00Z");
  private static final Instant SHIFT_END = Instant.parse("2026-11-27T05:00:00Z");

  private static final long USER_ID = 100L;
  private static final long STAFF_ID = 10L;
  private static final long CLINIC_A = 1L;
  private static final long CLINIC_B = 2L;
  private static final PatientActor STAFF_ACTOR = new PatientActor(USER_ID, UserRole.MEDICAL_STAFF);

  @Mock private ReceptionRepository repository;
  @Mock private PatientService patientService;
  @Mock private PatientMedicalHistoryService medicalHistoryService;
  @Mock private AppointmentBookingService bookingService;

  private ReceptionService service;

  private StaffClinicRow clinicA;
  private ClinicRoomOption clinicalRoom;
  private ClinicRoomOption labRoom;
  private ClinicRoomOption imagingRoom;
  private ClinicRoomOption vacRoom;

  @BeforeEach
  void setUp() {
    service =
        new ReceptionService(
            repository,
            patientService,
            medicalHistoryService,
            bookingService,
            Clock.fixed(NOW, ZONE));
    clinicA = new StaffClinicRow(CLINIC_A, UUID.randomUUID(), "CS1", "Cơ sở Quận 1", "OPENING");
    clinicalRoom = new ClinicRoomOption(11L, UUID.randomUUID(), "PK-01", "Phòng khám Nội 1");
    labRoom = new ClinicRoomOption(12L, UUID.randomUUID(), "XN-01", "Phòng Xét nghiệm Máu");
    imagingRoom =
        new ClinicRoomOption(13L, UUID.randomUUID(), "CDHA-01", "Phòng Siêu âm & X-quang");
    vacRoom = new ClinicRoomOption(14L, UUID.randomUUID(), "VAC-01", "Phòng Tiêm chủng Vắc-xin");
  }

  private static void assertFailsWith(ErrorCode expected, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }

  private PatientResponse patient(UUID publicId) {
    return new PatientResponse(
        publicId,
        "Nguyễn Văn An",
        LocalDate.of(1995, 5, 10),
        Gender.MALE,
        "0901234567",
        "an@example.com",
        "Quận 1, TP.HCM",
        IdentityType.CCCD,
        "079095001234",
        "Cục CSQLHC",
        PatientClass.PERSONAL,
        "BN2026-ABCDEF12",
        NOW.minusSeconds(86400),
        NOW.plusSeconds(86400L * 200),
        null,
        NOW.minusSeconds(86400),
        NOW.minusSeconds(86400));
  }

  private LockedAppointmentRow lockedAppointment(
      UUID appointmentPublicId,
      UUID patientPublicId,
      long clinicId,
      AppointmentStatus status,
      LocalDate workDate,
      Instant startAt,
      Instant shiftEndAt,
      Long existingVisitId) {
    return new LockedAppointmentRow(
        50L,
        appointmentPublicId,
        "AP-11112222333344445555666677778888",
        20L,
        patientPublicId,
        "Nguyễn Văn An",
        "0901234567",
        30L,
        status,
        startAt,
        startAt.plusSeconds(1200),
        40L,
        null,
        workDate,
        SHIFT_START,
        shiftEndAt,
        "Ca sáng",
        clinicId,
        clinicId == CLINIC_A ? "Cơ sở Quận 1" : "Cơ sở Quận 7",
        "OPENING",
        clinicalRoom.roomId(),
        clinicalRoom.roomPublicId(),
        clinicalRoom.code(),
        clinicalRoom.name(),
        "OPENING",
        "BS. Trần Minh",
        "Gói Tiêm ngừa HPV 3 mũi",
        null,
        existingVisitId);
  }

  // ---- lookup (REC-01, REC-02) --------------------------------------------------------------

  @Test
  void lookup_shouldRejectMissingOrMultipleSearchParams() {
    assertFailsWith(
        CommonErrorCode.VALIDATION_ERROR,
        () -> service.lookup(USER_ID, STAFF_ID, CLINIC_A, null, null, null, null));

    assertFailsWith(
        CommonErrorCode.VALIDATION_ERROR,
        () -> service.lookup(USER_ID, STAFF_ID, CLINIC_A, "AP-123", null, "0901234567", null));
  }

  @Test
  void lookup_shouldFailNotFound_whenQrDoesNotExist() {
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.findOpenRoomsByClinic(CLINIC_A)).thenReturn(List.of(clinicalRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(bookingService.findByQr("INVALID-QR", CLINIC_A)).thenReturn(Optional.empty());
    when(repository.findAppointmentRefByCodeOrQrHash(eq("INVALID-QR"), any()))
        .thenReturn(Optional.empty());

    assertFailsWith(
        ReceptionErrorCode.APPOINTMENT_NOT_FOUND,
        () -> service.lookup(USER_ID, STAFF_ID, null, "INVALID-QR", null, null, null));
  }

  @Test
  void lookup_shouldForbidQrBelongingToAnotherClinic() {
    UUID patientId = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.findOpenRoomsByClinic(CLINIC_A)).thenReturn(List.of(clinicalRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(bookingService.findByQr("AP-OTHER", CLINIC_A)).thenReturn(Optional.empty());
    when(repository.findAppointmentRefByCodeOrQrHash(eq("AP-OTHER"), any()))
        .thenReturn(
            Optional.of(
                new AppointmentLookupRef(
                    99L, UUID.randomUUID(), "AP-OTHER", 20L, patientId, CLINIC_B)));

    assertFailsWith(
        ReceptionErrorCode.CLINIC_ACCESS_DENIED,
        () -> service.lookup(USER_ID, STAFF_ID, CLINIC_A, "AP-OTHER", null, null, null));
  }

  @Test
  void lookup_shouldFailNotFound_whenPhoneMatchesNoPatient() {
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.findOpenRoomsByClinic(CLINIC_A)).thenReturn(List.of(clinicalRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(patientService.searchByPhone(eq("0999999999"), eq(STAFF_ACTOR), any()))
        .thenReturn(Page.empty());

    assertFailsWith(
        CommonErrorCode.NOT_FOUND,
        () -> service.lookup(USER_ID, STAFF_ID, null, null, null, "0999999999", null));
  }

  @Test
  void lookup_byQr_shouldReturnProfileHistoryCardWarningAndNearestAppointmentsFirst() {
    UUID patientId = UUID.randomUUID();
    UUID apptLateId = UUID.randomUUID();
    UUID apptEarlyId = UUID.randomUUID();
    UUID apptCancelledId = UUID.randomUUID();

    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.findOpenRoomsByClinic(CLINIC_A))
        .thenReturn(List.of(clinicalRoom, labRoom, imagingRoom, vacRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(bookingService.findByQr("AP-VALID", CLINIC_A))
        .thenReturn(
            Optional.of(
                new ReceptionReference(
                    apptLateId,
                    "AP-VALID",
                    20L,
                    AppointmentStatus.CONFIRMED,
                    NOW.minusSeconds(600),
                    NOW)));
    when(repository.findPatientPublicIdById(20L)).thenReturn(Optional.of(patientId));
    when(patientService.getProfile(patientId, STAFF_ACTOR)).thenReturn(patient(patientId));
    when(medicalHistoryService.getSummary(patientId, STAFF_ACTOR))
        .thenReturn(new MedicalHistorySummaryResponse(List.of(), List.of(), List.of()));
    when(patientService.checkCardValidity(patientId, STAFF_ACTOR))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.EXPIRING_SOON, true, 12, NOW.plusSeconds(86400L * 12)));

    AppointmentDetailRow earlyToday =
        new AppointmentDetailRow(
            2L,
            apptEarlyId,
            "AP-EARLY",
            AppointmentStatus.CONFIRMED,
            NOW.plusSeconds(3600),
            NOW.plusSeconds(4800),
            null,
            null,
            new BigDecimal("300000"),
            new BigDecimal("100000"),
            new BigDecimal("100000"),
            30L,
            TODAY,
            SHIFT_START,
            SHIFT_END,
            "MORNING",
            "Ca sáng",
            CLINIC_A,
            "Cơ sở Quận 1",
            "OPENING",
            clinicalRoom.roomId(),
            clinicalRoom.roomPublicId(),
            clinicalRoom.code(),
            clinicalRoom.name(),
            "OPENING",
            5L,
            "BS. A",
            "Nội tổng quát",
            null,
            null,
            "Khám nội",
            null,
            null,
            null);
    AppointmentDetailRow lateToday =
        new AppointmentDetailRow(
            1L,
            apptLateId,
            "AP-VALID",
            AppointmentStatus.CONFIRMED,
            NOW.minusSeconds(600),
            NOW.plusSeconds(600),
            null,
            null,
            new BigDecimal("500000"),
            new BigDecimal("200000"),
            BigDecimal.ZERO,
            30L,
            TODAY,
            SHIFT_START,
            SHIFT_END,
            "MORNING",
            "Ca sáng",
            CLINIC_A,
            "Cơ sở Quận 1",
            "OPENING",
            clinicalRoom.roomId(),
            clinicalRoom.roomPublicId(),
            clinicalRoom.code(),
            clinicalRoom.name(),
            "OPENING",
            5L,
            "BS. A",
            "Nội tổng quát",
            "Gói Tổng quát",
            "v1.0",
            null,
            null,
            null,
            null);
    AppointmentDetailRow cancelled =
        new AppointmentDetailRow(
            3L,
            apptCancelledId,
            "AP-CANCEL",
            AppointmentStatus.CANCELLED,
            NOW.plusSeconds(300),
            NOW.plusSeconds(1500),
            null,
            null,
            new BigDecimal("200000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            30L,
            TODAY,
            SHIFT_START,
            SHIFT_END,
            "MORNING",
            "Ca sáng",
            CLINIC_A,
            "Cơ sở Quận 1",
            "OPENING",
            clinicalRoom.roomId(),
            clinicalRoom.roomPublicId(),
            clinicalRoom.code(),
            clinicalRoom.name(),
            "OPENING",
            5L,
            "BS. A",
            "Nội tổng quát",
            null,
            null,
            "Khám lẻ",
            null,
            null,
            null);

    when(repository.findAppointmentsForPatient(patientId))
        .thenReturn(List.of(earlyToday, cancelled, lateToday));

    var result = service.lookup(USER_ID, STAFF_ID, null, "AP-VALID", null, null, null);

    assertThat(result.patients()).hasSize(1);
    var card = result.patients().getFirst();
    assertThat(card.cardAlert()).isEqualTo(CardAlertLevel.EXPIRING_SOON);
    assertThat(card.cardWarningMessage()).contains("12 ngày");
    assertThat(card.appointments())
        .extracting(ReceptionDtos.ReceptionAppointmentItem::appointmentPublicId)
        .containsExactly(apptLateId, apptEarlyId, apptCancelledId);
    assertThat(card.appointments().get(0).punctuality()).isEqualTo(PunctualityStatus.LATE);
    assertThat(card.appointments().get(0).paymentStatus()).isEqualTo(PaymentSummaryStatus.UNPAID);
    assertThat(card.appointments().get(0).receivable()).isTrue();
    assertThat(card.appointments().get(1).punctuality()).isEqualTo(PunctualityStatus.EARLY);
    assertThat(card.appointments().get(1).paymentStatus()).isEqualTo(PaymentSummaryStatus.PAID);
    assertThat(card.appointments().get(2).receivable()).isFalse();
    assertThat(card.appointments().get(2).ineligibleCode()).isEqualTo("APPOINTMENT_NOT_RECEIVABLE");
  }

  @Test
  void lookup_byPhone_shouldSupportMultiplePatientsWithSamePhone() {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.findOpenRoomsByClinic(CLINIC_A)).thenReturn(List.of(clinicalRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(patientService.searchByPhone(eq("0901234567"), eq(STAFF_ACTOR), any()))
        .thenReturn(new PageImpl<>(List.of(patient(p1), patient(p2))));
    when(medicalHistoryService.getSummary(any(), eq(STAFF_ACTOR)))
        .thenReturn(new MedicalHistorySummaryResponse(List.of(), List.of(), List.of()));
    when(patientService.checkCardValidity(any(), eq(STAFF_ACTOR)))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.ACTIVE, true, 100, NOW.plusSeconds(86400L * 100)));
    when(repository.findAppointmentsForPatient(any())).thenReturn(List.of());

    var response = service.lookup(USER_ID, STAFF_ID, null, null, null, "0901234567", null);

    assertThat(response.patients()).hasSize(2);
  }

  // ---- check-in & routing (REC-03, REC-04) --------------------------------------------------

  @Test
  void checkIn_shouldCreateVisitGenerateMultiOccurrenceOrdersAndRouteOnlyCurrentOccurrence() {
    UUID appointmentPublicId = UUID.randomUUID();
    UUID patientPublicId = UUID.randomUUID();
    LockedAppointmentRow locked =
        lockedAppointment(
            appointmentPublicId,
            patientPublicId,
            CLINIC_A,
            AppointmentStatus.CONFIRMED,
            TODAY,
            NOW.minusSeconds(300), // 5 minutes late — still receivable before shift ends
            SHIFT_END,
            null);

    CatalogItemRow clinicalItem =
        new CatalogItemRow(
            1L,
            UUID.randomUUID(),
            "Khám nội tổng quát",
            MedicalItemType.CLINICAL,
            null,
            new BigDecimal("200000"),
            1);
    CatalogItemRow labItem =
        new CatalogItemRow(
            2L,
            UUID.randomUUID(),
            "Công thức máu",
            MedicalItemType.LAB,
            "Nhịn ăn 8h",
            new BigDecimal("150000"),
            1);
    CatalogItemRow hpvItem =
        new CatalogItemRow(
            3L,
            UUID.randomUUID(),
            "Tiêm vắc-xin HPV 9 chủng",
            MedicalItemType.VACCINATION,
            null,
            new BigDecimal("2900000"),
            3);

    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.lockAppointmentForCheckIn(appointmentPublicId)).thenReturn(Optional.of(locked));
    when(patientService.checkCardValidity(patientPublicId, STAFF_ACTOR))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.EXPIRED, false, 0, NOW.minusSeconds(86400)));
    when(repository.findPackageVersionItems(40L))
        .thenReturn(List.of(clinicalItem, labItem, hpvItem));
    when(repository.insertVisit(any(), any(), eq(20L), eq(50L), eq(STAFF_ID), eq(NOW)))
        .thenReturn(77L);
    when(repository.findOpenRoomsByClinic(CLINIC_A))
        .thenReturn(List.of(clinicalRoom, labRoom, imagingRoom, vacRoom));
    when(repository.activeQueueCountsByRoom(eq(CLINIC_A), any(), any())).thenReturn(Map.of());
    when(repository.allocateQueueNumberForRoom(eq(clinicalRoom.roomId()), any(), any()))
        .thenReturn(1);
    when(repository.allocateQueueNumberForRoom(eq(labRoom.roomId()), any(), any())).thenReturn(4);
    when(repository.allocateQueueNumberForRoom(eq(vacRoom.roomId()), any(), any())).thenReturn(2);
    when(repository.findStaffName(STAFF_ID)).thenReturn(Optional.of("Lễ tân Hương"));
    when(repository.findOrdersByVisitId(77L)).thenReturn(List.of());

    var ticket =
        service.checkIn(
            USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, CLINIC_A, null));

    assertThat(ticket.status()).isEqualTo(MedicalVisitStatus.WAITING);
    assertThat(ticket.appointmentStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
    assertThat(ticket.punctuality()).isEqualTo(PunctualityStatus.LATE);
    assertThat(ticket.cardAlert()).isEqualTo(CardAlertLevel.EXPIRED);
    assertThat(ticket.cardWarningMessage()).isNotBlank();

    verify(repository)
        .markAppointmentCheckedIn(50L, STAFF_ID, 30L, locked.startAt(), locked.endAt(), NOW);

    // 1 clinical + 1 lab + 3 HPV occurrences = 5 inserted orders
    ArgumentCaptor<Integer> occCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Long> roomCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> performedVisitCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<MedicalOrderStatus> statusCaptor =
        ArgumentCaptor.forClass(MedicalOrderStatus.class);
    verify(repository, times(5))
        .insertOrder(
            any(),
            any(),
            eq(77L),
            any(),
            eq(MedicalOrderSource.PACKAGE),
            eq(40L),
            isNull(),
            occCaptor.capture(),
            roomCaptor.capture(),
            performedVisitCaptor.capture(),
            any(),
            any(),
            any(),
            statusCaptor.capture(),
            any(),
            any(),
            eq(NOW));

    assertThat(occCaptor.getAllValues()).containsExactly(1, 1, 1, 2, 3);
    assertThat(roomCaptor.getAllValues())
        .containsExactly(clinicalRoom.roomId(), labRoom.roomId(), vacRoom.roomId(), null, null);
    assertThat(performedVisitCaptor.getAllValues()).containsExactly(77L, 77L, 77L, null, null);
    assertThat(statusCaptor.getAllValues())
        .containsExactly(
            MedicalOrderStatus.WAITING,
            MedicalOrderStatus.WAITING,
            MedicalOrderStatus.WAITING,
            MedicalOrderStatus.ORDERED,
            MedicalOrderStatus.ORDERED);
  }

  @Test
  void checkIn_shouldRejectRevokedCard() {
    UUID appointmentPublicId = UUID.randomUUID();
    UUID patientPublicId = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CONFIRMED,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    null)));
    when(patientService.checkCardValidity(patientPublicId, STAFF_ACTOR))
        .thenReturn(new PatientCardValidityResponse(PatientCardStatus.REVOKED, false, 0, NOW));

    assertFailsWith(
        ReceptionErrorCode.PATIENT_CARD_REVOKED,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));
    verify(repository, never()).insertVisit(any(), any(), any(), any(), any(), any());
  }

  @Test
  void checkIn_shouldRejectDoubleCheckInAndClinicMismatchAndInvalidDateOrStatus() {
    UUID appointmentPublicId = UUID.randomUUID();
    UUID patientPublicId = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));

    // Already checked in
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CHECKED_IN,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    88L)));
    assertFailsWith(
        ReceptionErrorCode.APPOINTMENT_ALREADY_CHECKED_IN,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));

    // Different clinic (Clinic B)
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_B,
                    AppointmentStatus.CONFIRMED,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    null)));
    assertFailsWith(
        ReceptionErrorCode.CLINIC_ACCESS_DENIED,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));

    // Cancelled appointment
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CANCELLED,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    null)));
    assertFailsWith(
        ReceptionErrorCode.APPOINTMENT_NOT_RECEIVABLE,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));

    // Different day
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CONFIRMED,
                    TODAY.plusDays(1),
                    NOW.plusSeconds(86400),
                    SHIFT_END.plusSeconds(86400),
                    null)));
    assertFailsWith(
        ReceptionErrorCode.APPOINTMENT_NOT_TODAY,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));

    // Shift already ended
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CONFIRMED,
                    TODAY,
                    NOW.minusSeconds(7200),
                    NOW.minusSeconds(60),
                    null)));
    assertFailsWith(
        ReceptionErrorCode.SHIFT_ALREADY_ENDED,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));
  }

  @Test
  void checkIn_shouldTranslateDbUniqueViolationToAlreadyCheckedIn() {
    UUID appointmentPublicId = UUID.randomUUID();
    UUID patientPublicId = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CONFIRMED,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    null)));
    when(patientService.checkCardValidity(patientPublicId, STAFF_ACTOR))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.ACTIVE, true, 100, NOW.plusSeconds(86400L * 100)));
    when(repository.findPackageVersionItems(40L)).thenReturn(List.of());
    SQLException sqlEx = new SQLException("duplicate key", "23505");
    ConstraintViolationException cve =
        new ConstraintViolationException("dup", sqlEx, "medical_visits_appointment_id_key");
    when(repository.insertVisit(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("dup", cve));

    assertFailsWith(
        ReceptionErrorCode.APPOINTMENT_ALREADY_CHECKED_IN,
        () ->
            service.checkIn(
                USER_ID, STAFF_ID, new CreateReceptionRequest(appointmentPublicId, null, null)));
  }

  @Test
  void checkIn_shouldRejectManualRoomAssignmentInWrongClinicOrClosed() {
    UUID appointmentPublicId = UUID.randomUUID();
    UUID patientPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    UUID otherClinicRoomId = UUID.randomUUID();
    UUID closedRoomId = UUID.randomUUID();

    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.lockAppointmentForCheckIn(appointmentPublicId))
        .thenReturn(
            Optional.of(
                lockedAppointment(
                    appointmentPublicId,
                    patientPublicId,
                    CLINIC_A,
                    AppointmentStatus.CONFIRMED,
                    TODAY,
                    NOW,
                    SHIFT_END,
                    null)));
    when(patientService.checkCardValidity(patientPublicId, STAFF_ACTOR))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.ACTIVE, true, 100, NOW.plusSeconds(86400L * 100)));

    when(repository.findRoomByPublicId(otherClinicRoomId))
        .thenReturn(
            Optional.of(new RoomRow(99L, otherClinicRoomId, CLINIC_B, "XN-B", "Lab B", "OPENING")));
    assertFailsWith(
        ReceptionErrorCode.ROOM_NOT_IN_CLINIC,
        () ->
            service.checkIn(
                USER_ID,
                STAFF_ID,
                new CreateReceptionRequest(
                    appointmentPublicId,
                    CLINIC_A,
                    List.of(new ItemRoomAssignment(itemPublicId, otherClinicRoomId)))));

    when(repository.findRoomByPublicId(closedRoomId))
        .thenReturn(
            Optional.of(new RoomRow(98L, closedRoomId, CLINIC_A, "XN-C", "Lab Closed", "CLOSING")));
    assertFailsWith(
        ReceptionErrorCode.ROOM_NOT_OPEN,
        () ->
            service.checkIn(
                USER_ID,
                STAFF_ID,
                new CreateReceptionRequest(
                    appointmentPublicId,
                    CLINIC_A,
                    List.of(new ItemRoomAssignment(itemPublicId, closedRoomId)))));
  }

  @Test
  void routeOrder_shouldReassignRoomAndAllocateQueueNumber() {
    UUID visitPublicId = UUID.randomUUID();
    UUID orderPublicId = UUID.randomUUID();
    when(repository.findStaffClinics(STAFF_ID)).thenReturn(List.of(clinicA));
    when(repository.lockOrderForRouting(visitPublicId, orderPublicId))
        .thenReturn(
            Optional.of(
                new LockedOrderRow(
                    500L,
                    orderPublicId,
                    77L,
                    MedicalVisitStatus.WAITING,
                    CLINIC_A,
                    MedicalOrderStatus.ORDERED)));
    when(repository.findRoomByPublicId(imagingRoom.roomPublicId()))
        .thenReturn(
            Optional.of(
                new RoomRow(
                    imagingRoom.roomId(),
                    imagingRoom.roomPublicId(),
                    CLINIC_A,
                    imagingRoom.code(),
                    imagingRoom.name(),
                    "OPENING")));
    when(repository.allocateQueueNumberForRoom(eq(imagingRoom.roomId()), any(), any()))
        .thenReturn(7);
    when(repository.findOrderByPublicId(orderPublicId))
        .thenReturn(
            Optional.of(
                new ReceptionDtos.RoutedOrderResponse(
                    orderPublicId,
                    "ORD-1",
                    UUID.randomUUID(),
                    "Siêu âm bụng",
                    MedicalItemType.IMAGING,
                    MedicalOrderSource.PACKAGE,
                    1,
                    1,
                    true,
                    MedicalOrderStatus.WAITING,
                    imagingRoom.roomPublicId(),
                    imagingRoom.code(),
                    imagingRoom.name(),
                    7,
                    NOW,
                    null)));

    var routed =
        service.routeOrder(
            STAFF_ID,
            visitPublicId,
            orderPublicId,
            new UpdateOrderRoutingRequest(imagingRoom.roomPublicId()));

    assertThat(routed.roomPublicId()).isEqualTo(imagingRoom.roomPublicId());
    assertThat(routed.queueNumber()).isEqualTo(7);
    assertThat(routed.status()).isEqualTo(MedicalOrderStatus.WAITING);
    verify(repository).updateOrderRouting(500L, 77L, imagingRoom.roomId(), 7, NOW);
  }
}
