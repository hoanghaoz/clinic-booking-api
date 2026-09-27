package com.se100.clinic.doctor;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * package-private — chỉ định tuyến HTTP và convert request/response, KHÔNG chứa logic nghiệp vụ
 * (logic nằm ở {@link SpecialtyService}). Đây là điểm khác so với nhiều dự án NestJS nhỏ hay nhét
 * logic thẳng vào Controller — ở Spring, quy ước "Controller mỏng, chỉ gọi Service" nên được giữ
 * nghiêm để dễ test Service độc lập bằng unit test (không cần khởi HTTP server).
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody} — tương đương
 * {@code @Controller()} của NestJS (NestJS mặc định trả JSON, không cần decorator riêng), hoặc
 * {@code [ApiController]} của ASP.NET Core.
 */
@RestController
@RequestMapping("/api/v1/specialties")
class SpecialtyController {

  private final SpecialtyService specialtyService;

  SpecialtyController(SpecialtyService specialtyService) {
    this.specialtyService = specialtyService;
  }

  @PostMapping
  ResponseEntity<SpecialtyResponse> create(@Valid @RequestBody CreateSpecialtyRequest request) {
    SpecialtyResponse created = specialtyService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/specialties/" + created.id())).body(created);
  }

  @GetMapping
  List<SpecialtyResponse> list() {
    return specialtyService.list();
  }

  @GetMapping("/{id}")
  SpecialtyResponse getById(@PathVariable Long id) {
    return specialtyService.getById(id);
  }

  @PutMapping("/{id}")
  SpecialtyResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateSpecialtyRequest request) {
    return specialtyService.update(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> deactivate(@PathVariable Long id) {
    specialtyService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
