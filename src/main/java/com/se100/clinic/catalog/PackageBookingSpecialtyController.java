package com.se100.clinic.catalog;

import com.se100.clinic.shared.annotation.AdminOnly;
import com.se100.clinic.shared.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medical-packages/{packagePublicId}/specialties")
@RequiredArgsConstructor
class PackageBookingSpecialtyController {
  private final CatalogBookingService catalog;

  @GetMapping
  @Operation(summary = "Chuyên khoa nhận đặt lịch cho gói khám")
  ApiResult<Specialties> get(@PathVariable UUID packagePublicId) {
    return ApiResult.of(new Specialties(catalog.packageSpecialties(packagePublicId)));
  }

  @PutMapping
  @AdminOnly
  @Operation(summary = "Cấu hình chuyên khoa nhận đặt lịch cho gói khám")
  ApiResult<Specialties> replace(
      @PathVariable UUID packagePublicId, @Valid @RequestBody Specialties request) {
    return ApiResult.of(
        new Specialties(
            catalog.replacePackageSpecialties(packagePublicId, request.specialtyIds())));
  }

  record Specialties(@NotEmpty List<@NotNull @Positive Long> specialtyIds) {}
}
