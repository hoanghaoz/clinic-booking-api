package com.se100.clinic.config;

import com.se100.clinic.shared.ApiDocs;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi generates {@code /v3/api-docs} and Swagger UI just by being on the classpath;
 * this config adds title/description metadata and documents the shared error body. Like NestJS
 * {@code SwaggerModule.setup(...)}.
 *
 * <p>The frontend generates TypeScript types from {@code /v3/api-docs} with openapi-typescript (see
 * README), so the {@code ApiError} schema below becomes the FE's error type.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI clinicBookingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Clinic Booking API")
                .description(
                    "Hệ thống đặt lịch khám bệnh và dịch vụ y tế — xem docs/de-tai.md.\n\n"
                        + "Thành công: `{\"data\": ..., \"meta\": ...}` (meta chỉ có ở danh sách)."
                        + " Lỗi: `ApiError` (RFC 9457 + `code`)."
                        + " Chi tiết: docs/conventions/api-conventions.md")
                .version("v0"));
  }

  /**
   * Registers the {@code ApiError} schema (the shape {@code GlobalExceptionHandler} returns) and
   * adds the errors every endpoint can produce — 400 and 500 — so controllers only document their
   * own 404/409/....
   */
  @Bean
  public OpenApiCustomizer apiErrorCustomizer() {
    return openApi -> {
      Schema<?> fieldViolation =
          new ObjectSchema()
              .addProperty("field", new StringSchema().description("Tên field/tham số bị lỗi"))
              .addProperty("message", new StringSchema().description("Lý do"))
              .required(List.of("field", "message"));
      Schema<?> apiError =
          new ObjectSchema()
              .description("Body của mọi response lỗi (RFC 9457 ProblemDetail + `code`).")
              .addProperty("title", new StringSchema().description("Tên status HTTP"))
              .addProperty("status", new IntegerSchema().description("Status HTTP"))
              .addProperty("detail", new StringSchema().description("Mô tả cho người đọc"))
              .addProperty("instance", new StringSchema().description("Đường dẫn request"))
              .addProperty(
                  "code",
                  new StringSchema()
                      .description("Mã lỗi để FE rẽ nhánh, vd. VALIDATION_ERROR, SLOT_FULL"))
              .addProperty(
                  "errors",
                  new ArraySchema()
                      .items(new Schema<>().$ref("#/components/schemas/FieldViolation"))
                      .description("Chỉ có khi code = VALIDATION_ERROR"))
              .required(List.of("status", "code"));
      openApi
          .getComponents()
          .addSchemas("FieldViolation", fieldViolation)
          .addSchemas(ApiDocs.ERROR_SCHEMA_NAME, apiError);

      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(
                          operation -> {
                            addErrorResponse(
                                operation,
                                "400",
                                "VALIDATION_ERROR (kèm errors[]) hoặc INVALID_REQUEST");
                            addErrorResponse(operation, "500", "INTERNAL_ERROR");
                          }));
    };
  }

  private static void addErrorResponse(Operation operation, String status, String description) {
    if (operation.getResponses().containsKey(status)) {
      return;
    }
    operation
        .getResponses()
        .addApiResponse(
            status,
            new ApiResponse()
                .description(description)
                .content(
                    new Content()
                        .addMediaType(
                            ApiDocs.PROBLEM_JSON,
                            new MediaType().schema(new Schema<>().$ref(ApiDocs.ERROR_SCHEMA)))));
  }
}
