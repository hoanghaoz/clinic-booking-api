package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.shared.api.ApiResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** HTTP-level check of updating and soft-deleting medical items. */
class MedicalItemControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/medical-items";

  private static final ParameterizedTypeReference<ApiResult<MedicalItemResponseDto>> ONE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<List<MedicalItemResponseDto>>> LIST =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Object>> ERROR =
      new ParameterizedTypeReference<>() {};

  @Autowired private MedicalItemService medicalItemService;
  @Autowired private MedicalPackageService medicalPackageService;

  private static String tag() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private MedicalItemResponseDto newItem(String name) {
    return medicalItemService.createMedicalItem(
        new MedicalItemCreateDto(name, MedicalItemType.LAB, BigDecimal.TEN, "desc", "how"));
  }

  private static MedicalItemUpdateDto update(String name) {
    return new MedicalItemUpdateDto(
        name, MedicalItemType.IMAGING, new BigDecimal("99"), null, null);
  }

  @Test
  void update_shouldReplaceFields_andReturnActiveItem() {
    String tag = tag();
    MedicalItemResponseDto item = newItem("Before " + tag);

    ResponseEntity<ApiResult<MedicalItemResponseDto>> response =
        restTemplate.exchange(
            BASE + "/" + item.publicId(),
            HttpMethod.PUT,
            new HttpEntity<>(update("After " + tag)),
            ONE);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MedicalItemResponseDto data = response.getBody().data();
    assertThat(data.publicId()).isEqualTo(item.publicId());
    assertThat(data.name()).isEqualTo("After " + tag);
    assertThat(data.type()).isEqualTo(MedicalItemType.IMAGING);
    assertThat(data.price()).isEqualByComparingTo("99");
    assertThat(data.description()).isNull();
    assertThat(data.instruction()).isNull();
    assertThat(data.active()).isTrue();
  }

  @Test
  void update_shouldReturnProblemCodes() {
    String tag = tag();
    MedicalItemResponseDto first = newItem("First " + tag);
    newItem("Second " + tag);

    assertError(
        HttpMethod.PUT,
        BASE + "/" + first.publicId(),
        update("Second " + tag),
        HttpStatus.CONFLICT,
        "MEDICAL_ITEM_EXISTS");
    assertError(
        HttpMethod.PUT,
        BASE + "/" + UUID.randomUUID(),
        update("Any " + tag),
        HttpStatus.NOT_FOUND,
        "MEDICAL_ITEM_NOT_FOUND");
    assertError(
        HttpMethod.PUT,
        BASE + "/" + first.publicId(),
        new MedicalItemUpdateDto(" ", null, new BigDecimal("-1"), null, null),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
  }

  @Test
  void delete_shouldReturn204WithoutBody_andHideItemFromActiveSearch() {
    String tag = tag();
    MedicalItemResponseDto item = newItem("Retire " + tag);

    ResponseEntity<String> deleted =
        restTemplate.exchange(BASE + "/" + item.publicId(), HttpMethod.DELETE, null, String.class);
    // deleting again is not an error
    ResponseEntity<String> again =
        restTemplate.exchange(BASE + "/" + item.publicId(), HttpMethod.DELETE, null, String.class);

    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(deleted.getBody()).isNull();
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(search(tag, true)).isEmpty();
    assertThat(search(tag, false))
        .singleElement()
        .satisfies(found -> assertThat(found.active()).isFalse());
  }

  @Test
  void delete_shouldReturn404_whenItemDoesNotExist() {
    assertError(
        HttpMethod.DELETE,
        BASE + "/" + UUID.randomUUID(),
        null,
        HttpStatus.NOT_FOUND,
        "MEDICAL_ITEM_NOT_FOUND");
  }

  @Test
  void createPackage_shouldFailWithConflict_whenItemIsInactive() {
    String tag = tag();
    MedicalItemResponseDto item = newItem("Gone " + tag);
    medicalItemService.deactivateMedicalItem(item.publicId());

    var request =
        new MedicalPackageCreateDto(
            "PKG" + tag,
            "Package " + tag,
            null,
            null,
            null,
            null,
            null,
            PackageStatus.OPENING,
            BigDecimal.TEN,
            PackageClass.STANDARD,
            30,
            new MedicalPackageItemAddListDto(
                List.of(new MedicalPackageItemAddDto(item.publicId(), 1, 1))),
            null);

    assertError(
        HttpMethod.POST,
        "/api/v1/medical-packages",
        request,
        HttpStatus.CONFLICT,
        "MEDICAL_ITEM_INACTIVE");
  }

  private List<MedicalItemResponseDto> search(String tag, boolean active) {
    return restTemplate
        .exchange(BASE + "?keyword=" + tag + "&active=" + active, HttpMethod.GET, null, LIST)
        .getBody()
        .data();
  }

  private void assertError(
      HttpMethod method, String url, Object body, HttpStatus status, String code) {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(url, method, body == null ? null : new HttpEntity<>(body), ERROR);

    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).containsEntry("code", code);
  }
}
