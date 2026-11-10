package com.se100.clinic.patient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryResponse;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.enums.MedicalHistorySource;
import com.se100.clinic.patient.enums.MedicalHistoryType;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
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

/** HTTP-contract test of the medical history endpoints with a mocked service. */
@ExtendWith(MockitoExtension.class)
class PatientMedicalHistoryControllerTest {

  private static final UUID PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final PatientActor ACTOR = new PatientActor(11L, UserRole.DOCTOR);
  private static final TestingAuthenticationToken AUTH =
      new TestingAuthenticationToken("doctor", "n/a", "ROLE_DOCTOR");

  @Mock private PatientMedicalHistoryService historyService;
  @Mock private PatientActorResolver actors;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(actors.resolve(any())).thenReturn(ACTOR);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PatientMedicalHistoryController(historyService, actors))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static MedicalHistoryResponse entry(long id, MedicalHistoryType type) {
    return new MedicalHistoryResponse(
        id,
        type,
        "Dị ứng penicillin",
        null,
        MedicalHistorySource.DOCTOR,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  @Test
  void add_returns201_withLocation_andDataEnvelope() throws Exception {
    when(historyService.add(eq(PUBLIC_ID), any(), eq(ACTOR)))
        .thenReturn(entry(5, MedicalHistoryType.ALLERGY));

    mockMvc
        .perform(
            post("/api/v1/patients/" + PUBLIC_ID + "/medical-histories")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ALLERGY\",\"description\":\"Dị ứng penicillin\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/medical-histories/5"))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.source").value("DOCTOR"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void add_withMissingTypeAndBlankDescription_returns400_andNeverCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/patients/" + PUBLIC_ID + "/medical-histories")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(2));
    verifyNoInteractions(historyService);
  }

  @Test
  void list_returnsPagedList_andPassesTypeFilter() throws Exception {
    when(historyService.list(eq(PUBLIC_ID), eq(MedicalHistoryType.ALLERGY), eq(ACTOR), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(entry(5, MedicalHistoryType.ALLERGY)), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(
            get("/api/v1/patients/" + PUBLIC_ID + "/medical-histories")
                .param("type", "ALLERGY")
                .principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("ALLERGY"))
        .andExpect(jsonPath("$.meta.totalElements").value(1));
  }

  @Test
  void list_withUnknownTypeOrSortField_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/patients/" + PUBLIC_ID + "/medical-histories")
                .param("type", "BOGUS")
                .principal(AUTH))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v1/patients/" + PUBLIC_ID + "/medical-histories")
                .param("sort", "description")
                .principal(AUTH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(historyService);
  }

  @Test
  void summary_returnsThreeGroups() throws Exception {
    when(historyService.getSummary(PUBLIC_ID, ACTOR))
        .thenReturn(
            new MedicalHistorySummaryResponse(
                List.of(entry(1, MedicalHistoryType.PERSONAL)),
                List.of(),
                List.of(entry(3, MedicalHistoryType.ALLERGY))));

    mockMvc
        .perform(
            get("/api/v1/patients/" + PUBLIC_ID + "/medical-histories/summary").principal(AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.personal.length()").value(1))
        .andExpect(jsonPath("$.data.family.length()").value(0))
        .andExpect(jsonPath("$.data.allergies[0].id").value(3));
  }

  @Test
  void update_returns200() throws Exception {
    when(historyService.update(eq(5L), any(), eq(ACTOR)))
        .thenReturn(entry(5, MedicalHistoryType.ALLERGY));

    mockMvc
        .perform(
            put("/api/v1/medical-histories/5")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Dị ứng aspirin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(5));
  }

  @Test
  void update_withBlankDescription_returns400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/medical-histories/5")
                .principal(AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(historyService);
  }
}
