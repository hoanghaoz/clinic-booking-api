package com.se100.clinic.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.employee.StaffService;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.reception.ReceptionDtos.ClinicRoomOption;
import com.se100.clinic.reception.ReceptionDtos.ReceptionListItem;
import com.se100.clinic.reception.ReceptionDtos.ReceptionLookupResponse;
import com.se100.clinic.reception.ReceptionDtos.ReceptionTicketResponse;
import com.se100.clinic.reception.ReceptionDtos.RoutedOrderResponse;
import com.se100.clinic.reception.ReceptionDtos.StaffClinicResponse;
import com.se100.clinic.reception.enums.CardAlertLevel;
import com.se100.clinic.reception.enums.PunctualityStatus;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReceptionControllerTest {

  @Mock private ReceptionService receptionService;
  @Mock private AuthenticatedUserService users;
  @Mock private StaffService staffService;

  private MockMvc mockMvc;
  private TestingAuthenticationToken staffAuth;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ReceptionController(receptionService, users, staffService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    staffAuth = new TestingAuthenticationToken("staff_01", "n/a", "ROLE_MEDICAL_STAFF");
  }

  private void stubWorkingStaff() {
    when(users.userIdForPrincipal("staff_01")).thenReturn(Optional.of(100L));
    when(staffService.findWorkingStaffIdByUserId(100L)).thenReturn(Optional.of(10L));
  }

  @Test
  void clinics_returns200_withDataEnvelope() throws Exception {
    stubWorkingStaff();
    UUID clinicId = UUID.randomUUID();
    when(receptionService.staffClinics(10L))
        .thenReturn(
            List.of(
                new StaffClinicResponse(
                    1L,
                    clinicId,
                    "CS1",
                    "Cơ sở Quận 1",
                    "OPENING",
                    List.of(
                        new ClinicRoomOption(11L, UUID.randomUUID(), "PK-01", "Phòng khám 1")))));

    mockMvc
        .perform(get("/api/v1/receptions/clinics").principal(staffAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].clinicCode").value("CS1"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void lookup_delegatesToService_andReturns200() throws Exception {
    stubWorkingStaff();
    when(receptionService.lookup(100L, 10L, 1L, "AP-QR", null, null, null))
        .thenReturn(
            new ReceptionLookupResponse(
                1L, UUID.randomUUID(), "CS1", "Cơ sở 1", List.of(), List.of()));

    mockMvc
        .perform(
            get("/api/v1/receptions/lookup")
                .param("clinicId", "1")
                .param("qr", "AP-QR")
                .principal(staffAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clinicCode").value("CS1"))
        .andExpect(jsonPath("$.data.patients").isArray());
  }

  @Test
  void checkIn_returns201_withLocationHeaderAndTicketBody() throws Exception {
    stubWorkingStaff();
    UUID visitPublicId = UUID.randomUUID();
    UUID apptPublicId = UUID.randomUUID();
    when(receptionService.checkIn(eq(100L), eq(10L), any()))
        .thenReturn(
            new ReceptionTicketResponse(
                visitPublicId,
                "PTN-20261127-ABCD1234",
                MedicalVisitStatus.WAITING,
                Instant.EPOCH,
                10L,
                "Lễ tân Lan",
                1L,
                "Cơ sở Quận 1",
                apptPublicId,
                "AP-001",
                AppointmentStatus.CHECKED_IN,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1200),
                PunctualityStatus.ON_TIME,
                0L,
                UUID.randomUUID(),
                "Nguyễn Văn A",
                "0901234567",
                CardAlertLevel.NONE,
                null,
                "BS. Minh",
                "Ca sáng",
                "Phòng khám 1",
                "Gói Tổng quát",
                null,
                List.of()));

    mockMvc
        .perform(
            post("/api/v1/receptions")
                .principal(staffAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentPublicId\":\"" + apptPublicId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/receptions/" + visitPublicId))
        .andExpect(jsonPath("$.data.visitCode").value("PTN-20261127-ABCD1234"))
        .andExpect(jsonPath("$.data.appointmentStatus").value("CHECKED_IN"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void checkIn_withMissingAppointmentPublicId_returns400ValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/receptions")
                .principal(staffAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("appointmentPublicId"));

    verifyNoInteractions(receptionService);
  }

  @Test
  void checkIn_whenAlreadyCheckedIn_returns409ProblemDetail() throws Exception {
    stubWorkingStaff();
    when(receptionService.checkIn(eq(100L), eq(10L), any()))
        .thenThrow(
            new ConflictException(
                ReceptionErrorCode.APPOINTMENT_ALREADY_CHECKED_IN, "Lịch hẹn đã được tiếp nhận"));

    mockMvc
        .perform(
            post("/api/v1/receptions")
                .principal(staffAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentPublicId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_ALREADY_CHECKED_IN"));
  }

  @Test
  void list_returnsPagedDataAndMeta_andRejectsInvalidSort() throws Exception {
    stubWorkingStaff();
    ReceptionListItem item =
        new ReceptionListItem(
            UUID.randomUUID(),
            "PTN-20261127-00001111",
            MedicalVisitStatus.WAITING,
            Instant.EPOCH,
            UUID.randomUUID(),
            "AP-001",
            Instant.EPOCH,
            UUID.randomUUID(),
            "Nguyễn Văn A",
            "0901234567",
            "BS. Minh",
            "Ca sáng",
            "Phòng 1",
            "Gói Tổng quát",
            null,
            3,
            3);
    when(receptionService.list(eq(10L), isNull(), isNull(), isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/v1/receptions").principal(staffAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].visitCode").value("PTN-20261127-00001111"))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.totalElements").value(1));

    mockMvc
        .perform(get("/api/v1/receptions").param("sort", "password").principal(staffAuth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void routeOrder_updatesRoomAndReturns200() throws Exception {
    stubWorkingStaff();
    UUID visitId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    when(receptionService.routeOrder(eq(10L), eq(visitId), eq(orderId), any()))
        .thenReturn(
            new RoutedOrderResponse(
                orderId,
                "ORD-1",
                UUID.randomUUID(),
                "X-quang ngực",
                MedicalItemType.IMAGING,
                MedicalOrderSource.PACKAGE,
                1,
                1,
                true,
                MedicalOrderStatus.WAITING,
                roomId,
                "CDHA-01",
                "Phòng X-quang",
                5,
                Instant.EPOCH,
                null));

    mockMvc
        .perform(
            put("/api/v1/receptions/" + visitId + "/orders/" + orderId + "/routing")
                .principal(staffAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomPublicId\":\"" + roomId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roomCode").value("CDHA-01"))
        .andExpect(jsonPath("$.data.queueNumber").value(5));

    verify(receptionService).routeOrder(eq(10L), eq(visitId), eq(orderId), any());
  }

  @Test
  void request_withoutStaffProfile_returns403StaffProfileNotFound() throws Exception {
    when(users.userIdForPrincipal("staff_01")).thenReturn(Optional.of(100L));
    when(staffService.findWorkingStaffIdByUserId(100L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/receptions/clinics").principal(staffAuth))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("STAFF_PROFILE_NOT_FOUND"));
  }
}
