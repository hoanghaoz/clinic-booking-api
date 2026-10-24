package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.se100.clinic.doctor.DoctorDtos.DoctorResponse;
import com.se100.clinic.doctor.DoctorDtos.DoctorSpecialtyResponse;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorStatusRequest;
import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.ConflictException;
import com.se100.clinic.shared.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP contract of {@code /api/v1/doctors} with a mocked service (see SpecialtyControllerTest). */
@ExtendWith(MockitoExtension.class)
class DoctorControllerTest {

  @Mock private DoctorService doctorService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new DoctorController(doctorService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static DoctorResponse response(long id, String code, EmployeeStatus status) {
    return new DoctorResponse(
        id,
        100 + id,
        code,
        "Bác sĩ " + code,
        "ThS.BS",
        status,
        List.of(new DoctorSpecialtyResponse(1L, "NOI", "Nội tổng quát", true)),
        Instant.EPOCH,
        Instant.EPOCH);
  }

  @Test
  void create_returns201_withLocation_andDataEnvelope() throws Exception {
    when(doctorService.create(any())).thenReturn(response(7, "BS007", EmployeeStatus.WORKING));

    mockMvc
        .perform(
            post("/api/v1/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":107,"code":"BS007","name":"Nguyễn Văn A","degree":"ThS.BS",
                     "specialtyIds":[1]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/doctors/7"))
        .andExpect(jsonPath("$.data.code").value("BS007"))
        .andExpect(jsonPath("$.data.status").value("WORKING"))
        .andExpect(jsonPath("$.data.specialties[0].code").value("NOI"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void create_withMissingFields_returns400_listingEachField_andNeverCallsService()
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"name\":\"\",\"degree\":\"\",\"specialtyIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(5));

    verifyNoInteractions(doctorService);
  }

  @Test
  void create_whenCodeTaken_returns409WithModuleCode() throws Exception {
    when(doctorService.create(any()))
        .thenThrow(new ConflictException(DoctorErrorCode.DOCTOR_CODE_EXISTS, "Mã đã tồn tại"));

    mockMvc
        .perform(
            post("/api/v1/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":1,"code":"BS1","name":"A","degree":"BS","specialtyIds":[1]}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCTOR_CODE_EXISTS"));
  }

  @Test
  void list_withoutParams_usesDefaultSortByName() throws Exception {
    when(doctorService.list(eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(response(1, "BS1", EmployeeStatus.WORKING)), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/v1/doctors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.totalElements").value(1));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(doctorService).list(eq(null), eq(null), eq(null), pageable.capture());
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
  }

  @Test
  void list_passesFiltersAndSortToService() throws Exception {
    when(doctorService.list(any(), any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    mockMvc
        .perform(
            get("/api/v1/doctors")
                .param("keyword", "nguyen")
                .param("specialtyId", "3")
                .param("status", "NOT_WORKING")
                .param("size", "10")
                .param("sort", "-createdAt"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(doctorService)
        .list(eq("nguyen"), eq(3L), eq(EmployeeStatus.NOT_WORKING), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
  }

  @Test
  void list_withUnknownStatusOrSortField_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/doctors").param("status", "RETIRED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("status"));
    mockMvc
        .perform(get("/api/v1/doctors").param("sort", "userId"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("sort"));

    verifyNoInteractions(doctorService);
  }

  @Test
  void getById_returnsDataEnvelope() throws Exception {
    when(doctorService.getById(3L)).thenReturn(response(3, "BS3", EmployeeStatus.WORKING));

    mockMvc
        .perform(get("/api/v1/doctors/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(3))
        .andExpect(jsonPath("$.data.userId").value(103));
  }

  @Test
  void update_returnsDataEnvelope() throws Exception {
    when(doctorService.update(eq(3L), any()))
        .thenReturn(response(3, "BS3", EmployeeStatus.WORKING));

    mockMvc
        .perform(
            put("/api/v1/doctors/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"degree\":\"TS\",\"specialtyIds\":[1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("BS3"));
  }

  @Test
  void changeStatus_passesStatusToService() throws Exception {
    when(doctorService.changeStatus(eq(3L), any()))
        .thenReturn(response(3, "BS3", EmployeeStatus.NOT_WORKING));

    mockMvc
        .perform(
            patch("/api/v1/doctors/3/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOT_WORKING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("NOT_WORKING"));

    verify(doctorService)
        .changeStatus(3L, new UpdateDoctorStatusRequest(EmployeeStatus.NOT_WORKING));
  }

  @Test
  void changeStatus_withoutStatus_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/doctors/3/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"));

    verifyNoInteractions(doctorService);
  }
}
