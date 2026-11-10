package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.patient.PatientDtos.PatientCardResponse;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP-contract test of the patient endpoints with a mocked service: envelope, status codes, query
 * params. No Spring context and no DB. Role rules are covered in {@code PatientAuthorizationTest}
 * and {@code PatientServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

  private static final UUID PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final PatientActor ACTOR = new PatientActor(7L, UserRole.MEDICAL_STAFF);
  private static final TestingAuthenticationToken AUTH =
      new TestingAuthenticationToken("staff", "n/a", "ROLE_MEDICAL_STAFF");

  private static final String CREATE_BODY =
      """
      {"fullName":"Nguyễn Văn A","dateOfBirth":"1990-05-20","gender":"MALE",
       "phone":"0901234567","address":"Quận 3","identityType":"CCCD",
       "identityNumber":"079123456789","identityIssuer":"Cục CSQLHC"}
      """;

  @Mock private PatientService patientService;
  @Mock private PatientActorResolver actors;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(actors.resolve(any())).thenReturn(ACTOR);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PatientController(patientService, actors))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static PatientResponse response() {
    return new PatientResponse(
        PUBLIC_ID,
        "Nguyễn Văn A",
        LocalDate.of(1990, 5, 20),
        Gender.MALE,
        "0901234567",
        null,
        "Quận 3",
        "CCCD",
        "079123456789",
        "Cục CSQLHC",
        PatientClass.PERSONAL,
        "BN2026-ABCDEFGH",
        Instant.EPOCH,
        Instant.EPOCH,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  @Test
  void create_returns201_withLocation_andDataEnvelope_withoutMeta() throws Exception {
    when(patientService.create(any(), eq(ACTOR))).thenReturn(response());

    mockMvc
        .perform(
            post("/api/v1/patients")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/patients/" + PUBLIC_ID))
        .andExpect(jsonPath("$.data.publicId").value(PUBLIC_ID.toString()))
        .andExpect(jsonPath("$.data.cardNumber").value("BN2026-ABCDEFGH"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void create_withEmptyBody_returns400ValidationError_andNeverCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/patients")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(8));
    verifyNoInteractions(patientService);
  }

  @Test
  void me_returns200_withProfile() throws Exception {
    when(patientService.getMyProfile(ACTOR)).thenReturn(response());

    mockMvc
        .perform(get("/api/v1/patients/me").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"));
  }

  @Test
  void search_byPhone_returnsPagedList_withDefaultPagingAndSort() throws Exception {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(patientService.searchByPhone(eq("0901234567"), eq(ACTOR), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/v1/patients/search").param("phone", "0901234567").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalElements").value(1));

    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().toString()).startsWith("fullName: ASC");
  }

  @Test
  void search_byIdentityNumber_callsIdentityLookup() throws Exception {
    when(patientService.searchByIdentityNumber(eq("079123456789"), eq(ACTOR), any()))
        .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(
            get("/api/v1/patients/search").param("identityNumber", "079123456789").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].identityNumber").value("079123456789"));
  }

  @Test
  void search_withBothOrNeitherParam_returns400_andNeverCallsService() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/patients/search")
                .param("phone", "0901234567")
                .param("identityNumber", "079123456789")
                .principal(AUTH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get("/api/v1/patients/search").principal(AUTH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get("/api/v1/patients/search").param("phone", "  ").principal(AUTH))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(patientService);
  }

  @Test
  void search_withUnsupportedSortField_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/patients/search")
                .param("phone", "0901234567")
                .param("sort", "identityNumber")
                .principal(AUTH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(patientService);
  }

  @Test
  void getByPublicId_returns200() throws Exception {
    when(patientService.getProfile(PUBLIC_ID, ACTOR)).thenReturn(response());

    mockMvc
        .perform(get("/api/v1/patients/" + PUBLIC_ID).principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publicId").value(PUBLIC_ID.toString()));
  }

  @Test
  void getByPublicId_withMalformedUuid_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/patients/not-a-uuid").principal(AUTH))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(patientService);
  }

  @Test
  void update_returns200_withUpdatedProfile() throws Exception {
    when(patientService.update(eq(PUBLIC_ID), any(), eq(ACTOR))).thenReturn(response());

    mockMvc
        .perform(
            put("/api/v1/patients/" + PUBLIC_ID)
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Nguyễn Văn A","dateOfBirth":"1990-05-20","gender":"MALE",
                     "phone":"0901234567","address":"Quận 3"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"));
  }

  @Test
  void update_withFutureBirthDate_returns400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/patients/" + PUBLIC_ID)
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"A","dateOfBirth":"2999-01-01","gender":"MALE",
                     "phone":"0901234567","address":"Quận 3"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(patientService);
  }

  @Test
  void changeClass_returns200() throws Exception {
    when(patientService.changeClass(eq(PUBLIC_ID), any(), eq(ACTOR))).thenReturn(response());

    mockMvc
        .perform(
            patch("/api/v1/patients/" + PUBLIC_ID + "/class")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientClass\":\"VIP\"}"))
        .andExpect(status().isOk());
    verify(patientService).changeClass(eq(PUBLIC_ID), any(), eq(ACTOR));
  }

  @Test
  void changeClass_withMissingOrUnknownClass_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/patients/" + PUBLIC_ID + "/class")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            patch("/api/v1/patients/" + PUBLIC_ID + "/class")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientClass\":\"GOLD\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(patientService);
  }

  @Test
  void card_returns200_withStatus() throws Exception {
    when(patientService.getCard(PUBLIC_ID, ACTOR))
        .thenReturn(
            new PatientCardResponse(
                "BN2026-ABCDEFGH",
                Instant.EPOCH,
                Instant.parse("2027-01-01T00:00:00Z"),
                null,
                PatientCardStatus.ACTIVE));

    mockMvc
        .perform(get("/api/v1/patients/" + PUBLIC_ID + "/card").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cardNumber").value("BN2026-ABCDEFGH"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.revokedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void cardValidity_returns200() throws Exception {
    when(patientService.checkCardValidity(PUBLIC_ID, ACTOR))
        .thenReturn(
            new PatientCardValidityResponse(
                PatientCardStatus.EXPIRING_SOON, true, 12, Instant.parse("2027-01-01T00:00:00Z")));

    mockMvc
        .perform(get("/api/v1/patients/" + PUBLIC_ID + "/card/validity").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EXPIRING_SOON"))
        .andExpect(jsonPath("$.data.valid").value(true))
        .andExpect(jsonPath("$.data.daysRemaining").value(12));
  }
}
