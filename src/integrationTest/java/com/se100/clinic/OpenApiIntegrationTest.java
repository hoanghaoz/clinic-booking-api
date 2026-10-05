package com.se100.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pins the OpenAPI document the frontend generates its TypeScript types from (see README): the
 * shared envelope/error schemas must stay documented. If this fails after you changed {@code
 * ApiResult}, {@code GlobalExceptionHandler} or {@code OpenApiConfig}, update
 * docs/conventions/api-conventions.md too.
 */
@SuppressWarnings("unchecked")
class OpenApiIntegrationTest extends AbstractIntegrationTest {

  private Map<String, Object> apiDocs() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "/v3/api-docs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static Map<String, Object> map(Object node, String key) {
    return (Map<String, Object>) ((Map<String, Object>) node).get(key);
  }

  @Test
  void errorSchema_isDocumented_withCodeAndErrors() {
    Map<String, Object> schemas = map(apiDocs().get("components"), "schemas");

    Map<String, Object> apiError = map(schemas, "ApiError");
    assertThat(map(apiError, "properties")).containsKeys("status", "detail", "code", "errors");
    assertThat((List<String>) apiError.get("required")).contains("code", "status");
    assertThat(map(schemas, "FieldViolation")).containsKey("properties");
  }

  @Test
  void successEnvelope_isDocumented_withRequiredData() {
    Map<String, Object> schemas = map(apiDocs().get("components"), "schemas");

    Map<String, Object> single = map(schemas, "ApiResultSpecialtyResponse");
    assertThat((List<String>) single.get("required")).containsExactly("data");
    assertThat(schemas).containsKeys("ApiResultListSpecialtyResponse", "PageMeta");
    assertThat((List<String>) map(schemas, "PageMeta").get("required"))
        .containsExactlyInAnyOrder("page", "size", "totalElements", "totalPages");
  }

  @Test
  void everyOperation_documentsStandardErrors_andDeleteHasNoBody() {
    Map<String, Object> paths = (Map<String, Object>) apiDocs().get("paths");

    for (Object pathItem : paths.values()) {
      for (Object operation : ((Map<String, Object>) pathItem).values()) {
        assertThat(map(operation, "responses")).containsKeys("400", "500");
      }
    }
    Map<String, Object> delete = map(map(paths, "/api/v1/specialties/{id}"), "delete");
    assertThat(map(delete, "responses")).containsKey("204");
    assertThat(map(map(delete, "responses"), "204")).doesNotContainKey("content");
  }

  @Test
  void listEndpoint_documentsPaginationParams_withDefaultsAndLimits() {
    Map<String, Object> paths = (Map<String, Object>) apiDocs().get("paths");
    Map<String, Object> list = map(map(paths, "/api/v1/specialties"), "get");

    List<Map<String, Object>> parameters = (List<Map<String, Object>>) list.get("parameters");
    assertThat(parameters)
        .extracting(p -> (String) p.get("name"))
        .containsExactlyInAnyOrder("keyword", "active", "page", "size", "sort");
    Map<String, Object> size =
        parameters.stream().filter(p -> "size".equals(p.get("name"))).findFirst().orElseThrow();
    assertThat(map(size, "schema")).containsEntry("default", 20).containsEntry("maximum", 100);
  }
}
