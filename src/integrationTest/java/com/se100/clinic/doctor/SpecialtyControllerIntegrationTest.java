package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import com.se100.clinic.shared.ApiResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full-stack test of the sample module: HTTP -> controller -> service -> Postgres, asserting the
 * contract in docs/conventions/api-conventions.md (envelope, pagination, filter, sort, errors).
 *
 * <p>The DB is shared between tests (see {@link AbstractIntegrationTest}), so every test tags its
 * data with a fresh {@code tag} and filters list calls by it ({@code keyword=<tag>}).
 */
class SpecialtyControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/specialties";

  private static final ParameterizedTypeReference<ApiResult<SpecialtyResponse>> ONE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<List<SpecialtyResponse>>> PAGE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Object>> ERROR =
      new ParameterizedTypeReference<>() {};

  private static String newTag() {
    return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private SpecialtyResponse create(String code, String name) {
    ResponseEntity<ApiResult<SpecialtyResponse>> response =
        restTemplate.exchange(
            BASE,
            HttpMethod.POST,
            new HttpEntity<>(new CreateSpecialtyRequest(code, name, null)),
            ONE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().data();
  }

  /**
   * {@code query} may contain {@code {name}} placeholders filled from {@code vars} (URL-encoded).
   */
  private ResponseEntity<ApiResult<List<SpecialtyResponse>>> list(String query, Object... vars) {
    return restTemplate.exchange(BASE + "?" + query, HttpMethod.GET, null, PAGE, vars);
  }

  private ResponseEntity<Map<String, Object>> listExpectingError(String query) {
    return restTemplate.exchange(BASE + "?" + query, HttpMethod.GET, null, ERROR);
  }

  // ---- single-object envelope + status codes -------------------------------------------------

  @Test
  void createThenGet_shouldRoundTripThroughRealDatabase_inDataEnvelope() {
    String tag = newTag();

    ResponseEntity<ApiResult<SpecialtyResponse>> created =
        restTemplate.exchange(
            BASE,
            HttpMethod.POST,
            new HttpEntity<>(new CreateSpecialtyRequest(tag, "Nhi khoa " + tag, "Khám nhi")),
            ONE);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long id = created.getBody().data().id();
    assertThat(created.getHeaders().getLocation()).hasPath(BASE + "/" + id);
    assertThat(created.getBody().meta()).isNull();

    ResponseEntity<ApiResult<SpecialtyResponse>> fetched =
        restTemplate.exchange(BASE + "/" + id, HttpMethod.GET, null, ONE);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody().data().code()).isEqualTo(tag);
    assertThat(fetched.getBody().data().active()).isTrue();
    assertThat(fetched.getBody().data().createdAt()).isNotNull();
  }

  @Test
  void update_shouldChangeNameAndDescription_inDataEnvelope() {
    SpecialtyResponse created = create(newTag(), "Tên cũ");

    ResponseEntity<ApiResult<SpecialtyResponse>> updated =
        restTemplate.exchange(
            BASE + "/" + created.id(),
            HttpMethod.PUT,
            new HttpEntity<>(new UpdateSpecialtyRequest("Tên mới", "Mô tả mới")),
            ONE);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().data().name()).isEqualTo("Tên mới");
    assertThat(updated.getBody().data().code()).isEqualTo(created.code());
  }

  @Test
  void delete_shouldReturn204WithNoBody_andSoftDeactivate() {
    SpecialtyResponse created = create(newTag(), "Sắp ngưng");

    ResponseEntity<String> deleted =
        restTemplate.exchange(BASE + "/" + created.id(), HttpMethod.DELETE, null, String.class);

    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(deleted.getBody()).isNull();
    ResponseEntity<ApiResult<SpecialtyResponse>> fetched =
        restTemplate.exchange(BASE + "/" + created.id(), HttpMethod.GET, null, ONE);
    assertThat(fetched.getBody().data().active()).isFalse();
  }

  // ---- errors ---------------------------------------------------------------------------------

  @Test
  void create_shouldReturn409WithBusinessCode_whenCodeAlreadyExists() {
    String tag = newTag();
    create(tag, "Lần một");

    ResponseEntity<Map<String, Object>> second =
        restTemplate.exchange(
            BASE,
            HttpMethod.POST,
            new HttpEntity<>(new CreateSpecialtyRequest(tag, "Lần hai", null)),
            ERROR);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(second.getBody()).containsEntry("code", "SPECIALTY_CODE_EXISTS");
    assertThat(second.getBody()).containsEntry("status", 409);
  }

  @Test
  void getById_shouldReturn404WithBusinessCode_whenNotFound() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(BASE + "/999999", HttpMethod.GET, null, ERROR);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", "SPECIALTY_NOT_FOUND");
    assertThat(response.getBody()).containsEntry("instance", BASE + "/999999");
  }

  @Test
  void create_shouldReturn400ValidationErrorWithFieldList_whenBodyInvalid() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            BASE,
            HttpMethod.POST,
            new HttpEntity<>(new CreateSpecialtyRequest("", "", null)),
            ERROR);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
    assertThat(errors).extracting(e -> e.get("field")).containsExactlyInAnyOrder("code", "name");
    assertThat(errors).allSatisfy(e -> assertThat(e.get("message")).isNotBlank());
  }

  @Test
  void unknownUrl_shouldReturn404WithCode() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/api/v1/does-not-exist", HttpMethod.GET, null, ERROR);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
  }

  // ---- pagination -----------------------------------------------------------------------------

  @Test
  void list_shouldPaginateOneBased_withMetadata() {
    String tag = newTag();
    for (int i = 1; i <= 5; i++) {
      create(tag + "_" + i, "Khoa " + tag + " " + i);
    }

    var page2 = list("keyword=" + tag + "&page=2&size=2&sort=code").getBody();
    assertThat(page2.data())
        .extracting(SpecialtyResponse::code)
        .containsExactly(tag + "_3", tag + "_4");
    assertThat(page2.meta()).isEqualTo(new ApiResult.PageMeta(2, 2, 5, 3));

    var lastPage = list("keyword=" + tag + "&page=3&size=2&sort=code").getBody();
    assertThat(lastPage.data()).extracting(SpecialtyResponse::code).containsExactly(tag + "_5");

    // Past the end is NOT an error: empty data, but meta still tells the truth.
    var beyond = list("keyword=" + tag + "&page=4&size=2").getBody();
    assertThat(beyond.data()).isEmpty();
    assertThat(beyond.meta().totalElements()).isEqualTo(5);
    assertThat(beyond.meta().page()).isEqualTo(4);
  }

  @Test
  void list_withoutPagingParams_shouldUseDefaultPage1Size20() {
    var body = list("").getBody();

    assertThat(body.meta().page()).isEqualTo(1);
    assertThat(body.meta().size()).isEqualTo(20);
    assertThat(body.data()).hasSizeLessThanOrEqualTo(20);
  }

  @Test
  void list_shouldRejectInvalidPaging_with400ValidationError() {
    for (String bad : List.of("page=0", "page=-1", "size=0", "size=101", "page=abc", "size=x")) {
      var response = listExpectingError(bad);

      assertThat(response.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).as(bad).containsEntry("code", "VALIDATION_ERROR");
      assertThat(response.getBody()).as(bad).containsKey("errors");
    }
  }

  // ---- sort -----------------------------------------------------------------------------------

  @Test
  void list_shouldSortByNameAscendingByDefault_andDescendingWithMinusPrefix() {
    String tag = newTag();
    create(tag + "_B", "Beta " + tag);
    create(tag + "_A", "Alpha " + tag);
    create(tag + "_C", "Gamma " + tag);

    var byDefault = list("keyword=" + tag).getBody();
    assertThat(byDefault.data())
        .extracting(SpecialtyResponse::code)
        .containsExactly(tag + "_A", tag + "_B", tag + "_C");

    var descending = list("keyword=" + tag + "&sort=-name").getBody();
    assertThat(descending.data())
        .extracting(SpecialtyResponse::code)
        .containsExactly(tag + "_C", tag + "_B", tag + "_A");
  }

  @Test
  void list_shouldRejectSortOnUnlistedField_with400() {
    var response = listExpectingError("sort=description");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
  }

  // ---- filter ---------------------------------------------------------------------------------

  @Test
  void list_shouldFilterByKeyword_caseInsensitive_onNameOrCode() {
    String tag = newTag();
    create(tag + "_TIM", "Tim mạch");
    create(tag + "_DA", "Da liễu");

    // keyword matches the code of one and the name of none else
    assertThat(list("keyword=" + tag.toLowerCase() + "_tim").getBody().data())
        .extracting(SpecialtyResponse::code)
        .containsExactly(tag + "_TIM");
    // keyword matches the name
    assertThat(list("keyword=" + tag + "&sort=code").getBody().data()).hasSize(2);
    assertThat(list("keyword={k}", "LIỄU").getBody().data())
        .extracting(SpecialtyResponse::code)
        .contains(tag + "_DA");
  }

  @Test
  void list_keywordWildcardsAreLiteral_notSqlPatterns() {
    String tag = newTag();
    create(tag + "_1", "Giảm 50% " + tag);
    create(tag + "_2", "Giảm 500 " + tag);

    // An unescaped "50%" would also match "500"; escaped, only the literal percent sign matches.
    var found = list("keyword={k}", "50% " + tag).getBody();
    assertThat(found.data()).extracting(SpecialtyResponse::code).containsExactly(tag + "_1");
    // A lone "%" or "_" must not behave as match-everything.
    assertThat(list("keyword={k}", tag + "_%").getBody().data()).isEmpty();
  }

  @Test
  void list_shouldFilterByActive_andCombineWithKeywordAndPaging() {
    String tag = newTag();
    SpecialtyResponse stays = create(tag + "_1", "Giữ " + tag);
    SpecialtyResponse goes = create(tag + "_2", "Ngưng " + tag);
    restTemplate.exchange(BASE + "/" + goes.id(), HttpMethod.DELETE, null, String.class);

    assertThat(list("keyword=" + tag + "&active=true").getBody().data())
        .extracting(SpecialtyResponse::id)
        .containsExactly(stays.id());
    assertThat(list("keyword=" + tag + "&active=false").getBody().data())
        .extracting(SpecialtyResponse::id)
        .containsExactly(goes.id());
    var both = list("keyword=" + tag + "&size=1").getBody();
    assertThat(both.meta().totalElements()).isEqualTo(2);
    assertThat(both.meta().totalPages()).isEqualTo(2);
  }

  @Test
  void list_shouldRejectNonBooleanActive_with400() {
    var response = listExpectingError("active=maybe");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
  }

  @Test
  void list_shouldIgnoreUnknownQueryParams() {
    assertThat(list("foo=bar").getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
