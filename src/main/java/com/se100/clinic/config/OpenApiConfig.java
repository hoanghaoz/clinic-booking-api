package com.se100.clinic.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi generates {@code /v3/api-docs} and Swagger UI just by being on the classpath;
 * this bean only adds title/description metadata. Like NestJS {@code SwaggerModule.setup(...)}.
 *
 * <p>The frontend generates TypeScript types from {@code /v3/api-docs} with openapi-typescript (see
 * README).
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI clinicBookingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Clinic Booking API")
                .description("Hệ thống đặt lịch khám bệnh và dịch vụ y tế — xem docs/de-tai.md")
                .version("v0"));
  }
}
