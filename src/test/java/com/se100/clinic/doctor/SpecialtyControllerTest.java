package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.shared.GlobalExceptionHandler;
import com.se100.clinic.shared.NotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP-contract test of the sample module with a mocked service: response envelope, status codes,
 * query-param handling. No Spring context and no DB, so it is fast; the full stack is covered by
 * {@code SpecialtyControllerIntegrationTest}.
 *
 * <p>Copy this when testing a new controller: it pins what the frontend relies on.
 */
@ExtendWith(MockitoExtension.class)
class SpecialtyControllerTest {

  @Mock private SpecialtyService specialtyService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SpecialtyController(specialtyService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static SpecialtyResponse response(long id, String code) {
    return new SpecialtyResponse(id, code, "Tên " + code, null, true, Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  void create_returns201_withLocation_andDataEnvelope_withoutMeta() throws Exception {
    when(specialtyService.create(any())).thenReturn(response(7, "NHI"));

    mockMvc
        .perform(
            post("/api/v1/specialties")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NHI\",\"name\":\"Nhi khoa\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/specialties/7"))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.code").value("NHI"))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void create_withBlankFields_returns400ValidationError_andNeverCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/specialties")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(2));

    verifyNoInteractions(specialtyService);
  }

  @Test
  void list_withoutParams_usesDefaultsAndReturnsDataArrayWithMeta() throws Exception {
    Page<SpecialtyResponse> page =
        new PageImpl<>(List.of(response(1, "A"), response(2, "B")), PageRequest.of(0, 20), 2);
    when(specialtyService.list(eq(null), eq(null), any(Pageable.class))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/specialties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].code").value("A"))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalElements").value(2))
        .andExpect(jsonPath("$.meta.totalPages").value(1));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(specialtyService).list(eq(null), eq(null), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
  }

  @Test
  void list_passesFiltersPageAndSortToService() throws Exception {
    when(specialtyService.list(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 6));

    mockMvc
        .perform(
            get("/api/v1/specialties")
                .param("keyword", "tim")
                .param("active", "true")
                .param("page", "2")
                .param("size", "5")
                .param("sort", "-createdAt,name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.meta.page").value(2))
        .andExpect(jsonPath("$.meta.totalPages").value(2));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(specialtyService).list(eq("tim"), eq(true), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("name"), Sort.Order.asc("id")));
  }

  @Test
  void list_sortWithRepeatedParam_isAlsoAccepted() throws Exception {
    when(specialtyService.list(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/specialties").param("sort", "-code").param("sort", "name"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(specialtyService).list(any(), any(), pageable.capture());
    assertThat(pageable.getValue().getSort().stream().map(Sort.Order::getProperty))
        .containsExactly("code", "name", "id");
  }

  @Test
  void list_withInvalidPaging_returns400ValidationError_listingEveryBadParam() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/specialties")
                .param("page", "0")
                .param("size", "101")
                .param("sort", "password"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[1].field").value("size"))
        .andExpect(jsonPath("$.errors[2].field").value("sort"));

    verify(specialtyService, never()).list(any(), any(), any());
  }

  @Test
  void list_withNonNumericPageOrBadBoolean_returns400ValidationError() throws Exception {
    mockMvc
        .perform(get("/api/v1/specialties").param("page", "abc").param("active", "maybe"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(specialtyService);
  }

  @Test
  void getById_returnsDataEnvelope() throws Exception {
    when(specialtyService.getById(3L)).thenReturn(response(3, "TIM"));

    mockMvc
        .perform(get("/api/v1/specialties/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(3))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void getById_whenMissing_returns404WithModuleCode() throws Exception {
    when(specialtyService.getById(99L))
        .thenThrow(new NotFoundException(SpecialtyErrorCode.SPECIALTY_NOT_FOUND, "Không tìm thấy"));

    mockMvc
        .perform(get("/api/v1/specialties/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPECIALTY_NOT_FOUND"));
  }

  @Test
  void update_returnsDataEnvelope() throws Exception {
    when(specialtyService.update(eq(3L), any())).thenReturn(response(3, "TIM"));

    mockMvc
        .perform(
            put("/api/v1/specialties/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tim mạch\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("TIM"));
  }

  @Test
  void delete_returns204_withEmptyBody() throws Exception {
    mockMvc
        .perform(delete("/api/v1/specialties/3"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(specialtyService).deactivate(3L);
  }
}
