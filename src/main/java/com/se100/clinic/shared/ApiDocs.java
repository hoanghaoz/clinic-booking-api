package com.se100.clinic.shared;

/**
 * Constants for Swagger annotations, so every controller documents errors the same way. The {@code
 * ApiError} schema is registered once in {@code com.se100.clinic.config.OpenApiConfig}.
 *
 * <pre>{@code
 * @ApiResponse(
 *     responseCode = "404",
 *     description = "SPECIALTY_NOT_FOUND",
 *     content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
 * }</pre>
 *
 * 400 and 500 are added to every operation automatically; only document the module-specific ones
 * (404, 409...) on the endpoint.
 */
public final class ApiDocs {

  public static final String PROBLEM_JSON = "application/problem+json";
  public static final String ERROR_SCHEMA_NAME = "ApiError";
  public static final String ERROR_SCHEMA = "#/components/schemas/" + ERROR_SCHEMA_NAME;

  private ApiDocs() {}
}
