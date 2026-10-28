package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionResponseDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.shared.api.ApiResult;
import java.math.BigDecimal;
import java.time.Instant;
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

/** HTTP-level check of the package detail and version endpoints, including their error codes. */
class MedicalPackageControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/medical-packages";

  private static final ParameterizedTypeReference<ApiResult<MedicalPackageDetailResponseDto>>
      DETAIL = new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<List<PackageVersionResponseDto>>>
      VERSIONS = new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<PackageVersionDetailResponseDto>>
      VERSION = new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Object>> ERROR =
      new ParameterizedTypeReference<>() {};

  @Autowired private MedicalPackageService medicalPackageService;
  @Autowired private MedicalItemService medicalItemService;

  private MedicalItemResponseDto newItem() {
    return medicalItemService.createMedicalItem(
        new MedicalItemCreateDto(
            "Item " + UUID.randomUUID(), MedicalItemType.LAB, BigDecimal.TEN, null, "How to"));
  }

  private MedicalPackageResponseDto newPackage(MedicalItemResponseDto item) {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    return medicalPackageService.createMedicalPackage(
        new MedicalPackageCreateDto(
            "PKG" + tag,
            "Package " + tag,
            null,
            null,
            null,
            null,
            null,
            PackageStatus.OPENING,
            new BigDecimal("1000000"),
            PackageClass.STANDARD,
            30,
            new MedicalPackageItemAddListDto(
                List.of(new MedicalPackageItemAddDto(item.publicId(), 2, 1))),
            Instant.parse("2026-01-01T00:00:00Z")));
  }

  private void changePrice(UUID packagePublicId, String price, String effectiveFrom) {
    medicalPackageService.updateMedicalPackage(
        new MedicalPackageUpdateDto(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal(price),
            null,
            null,
            null,
            Instant.parse(effectiveFrom)),
        packagePublicId);
  }

  @Test
  void getMedicalPackage_shouldReturnCurrentVersionWithItems() {
    MedicalItemResponseDto item = newItem();
    MedicalPackageResponseDto created = newPackage(item);
    changePrice(created.publicId(), "2000000", "2026-02-01T00:00:00Z");

    ResponseEntity<ApiResult<MedicalPackageDetailResponseDto>> response =
        restTemplate.exchange(BASE + "/" + created.publicId(), HttpMethod.GET, null, DETAIL);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MedicalPackageDetailResponseDto data = response.getBody().data();
    assertThat(data.publicId()).isEqualTo(created.publicId());
    assertThat(data.version()).isEqualTo("v1.1");
    assertThat(data.price()).isEqualByComparingTo("2000000");
    // the update sent no items, so the new version kept the old ones
    assertThat(data.medicalItems())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.medicalItemPublicId()).isEqualTo(item.publicId());
              assertThat(line.quantity()).isEqualTo(2);
              assertThat(line.instruction()).isEqualTo("How to");
            });
  }

  @Test
  void listVersions_shouldReturnNewestFirst_withMeta() {
    MedicalPackageResponseDto created = newPackage(newItem());
    changePrice(created.publicId(), "2000000", "2026-02-01T00:00:00Z");
    changePrice(created.publicId(), "3000000", "2026-03-01T00:00:00Z");

    ResponseEntity<ApiResult<List<PackageVersionResponseDto>>> response =
        restTemplate.exchange(
            BASE + "/" + created.publicId() + "/versions", HttpMethod.GET, null, VERSIONS);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApiResult<List<PackageVersionResponseDto>> body = response.getBody();
    assertThat(body.data())
        .extracting(PackageVersionResponseDto::version)
        .containsExactly("v1.2", "v1.1", "v1.0");
    assertThat(body.data())
        .extracting(PackageVersionResponseDto::current)
        .containsExactly(true, false, false);
    // each version ends exactly when the next one starts
    assertThat(body.data().get(2).effectiveTo()).isEqualTo(body.data().get(1).effectiveFrom());
    assertThat(body.data().get(2).price()).isEqualByComparingTo("1000000");
    assertThat(body.meta().totalElements()).isEqualTo(3);
  }

  @Test
  void listVersions_shouldSupportPagingAndAscendingSort() {
    MedicalPackageResponseDto created = newPackage(newItem());
    changePrice(created.publicId(), "2000000", "2026-02-01T00:00:00Z");

    ResponseEntity<ApiResult<List<PackageVersionResponseDto>>> response =
        restTemplate.exchange(
            BASE + "/" + created.publicId() + "/versions?size=1&page=2&sort=effectiveFrom",
            HttpMethod.GET,
            null,
            VERSIONS);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().data())
        .extracting(PackageVersionResponseDto::version)
        .containsExactly("v1.1");
    assertThat(response.getBody().meta().totalPages()).isEqualTo(2);
  }

  @Test
  void listVersions_shouldRejectUnknownSortField() {
    MedicalPackageResponseDto created = newPackage(newItem());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            BASE + "/" + created.publicId() + "/versions?sort=version",
            HttpMethod.GET,
            null,
            ERROR);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
  }

  @Test
  void getVersion_shouldReturnOldVersionWithItsItems() {
    MedicalItemResponseDto item = newItem();
    MedicalPackageResponseDto created = newPackage(item);
    changePrice(created.publicId(), "2000000", "2026-02-01T00:00:00Z");

    ResponseEntity<ApiResult<PackageVersionDetailResponseDto>> response =
        restTemplate.exchange(
            BASE + "/" + created.publicId() + "/versions/v1.0", HttpMethod.GET, null, VERSION);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    PackageVersionDetailResponseDto data = response.getBody().data();
    assertThat(data.version()).isEqualTo("v1.0");
    assertThat(data.current()).isFalse();
    assertThat(data.price()).isEqualByComparingTo("1000000");
    assertThat(data.medicalItems())
        .extracting("medicalItemPublicId")
        .containsExactly(item.publicId());
  }

  @Test
  void shouldReturn404WithCode_whenPackageOrVersionIsUnknown() {
    MedicalPackageResponseDto created = newPackage(newItem());

    assertNotFound(BASE + "/" + UUID.randomUUID(), "MEDICAL_PACKAGE_NOT_FOUND");
    assertNotFound(BASE + "/" + UUID.randomUUID() + "/versions", "MEDICAL_PACKAGE_NOT_FOUND");
    assertNotFound(BASE + "/" + UUID.randomUUID() + "/versions/v1.0", "MEDICAL_PACKAGE_NOT_FOUND");
    assertNotFound(
        BASE + "/" + created.publicId() + "/versions/v9.9", "MEDICAL_PACKAGE_VERSION_NOT_FOUND");
  }

  private MedicalPackageItemAddDto line(MedicalItemResponseDto item, int quantity, int position) {
    return new MedicalPackageItemAddDto(item.publicId(), quantity, position);
  }

  private ResponseEntity<ApiResult<PackageVersionDetailResponseDto>> send(
      HttpMethod method, String path, Object body) {
    return restTemplate.exchange(
        BASE + path, method, body == null ? null : new HttpEntity<>(body), VERSION);
  }

  private void assertError(
      HttpMethod method, String path, Object body, HttpStatus status, String code) {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            BASE + path, method, body == null ? null : new HttpEntity<>(body), ERROR);

    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).containsEntry("code", code);
  }

  @Test
  void addItem_shouldCreateNewVersion_andLeaveTheOldOneUntouched() {
    MedicalItemResponseDto first = newItem();
    MedicalItemResponseDto second = newItem();
    MedicalPackageResponseDto created = newPackage(first);
    String path = "/" + created.publicId() + "/items";

    ResponseEntity<ApiResult<PackageVersionDetailResponseDto>> response =
        send(HttpMethod.POST, path, line(second, 5, 2));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo(BASE + "/" + created.publicId() + "/versions/v1.1");
    PackageVersionDetailResponseDto data = response.getBody().data();
    assertThat(data.version()).isEqualTo("v1.1");
    assertThat(data.current()).isTrue();
    assertThat(data.medicalItems())
        .extracting("medicalItemPublicId", "quantity", "position")
        .containsExactly(tuple(first.publicId(), 2, 1), tuple(second.publicId(), 5, 2));
    PackageVersionDetailResponseDto old =
        restTemplate
            .exchange(
                BASE + "/" + created.publicId() + "/versions/v1.0", HttpMethod.GET, null, VERSION)
            .getBody()
            .data();
    assertThat(old.current()).isFalse();
    assertThat(old.medicalItems()).hasSize(1);
    assertThat(old.effectiveTo()).isEqualTo(data.effectiveFrom());
  }

  @Test
  void addItem_shouldReturnConflictCodes() {
    MedicalItemResponseDto first = newItem();
    MedicalItemResponseDto retired = newItem();
    medicalItemService.deactivateMedicalItem(retired.publicId());
    MedicalPackageResponseDto created = newPackage(first);
    String path = "/" + created.publicId() + "/items";

    assertError(
        HttpMethod.POST,
        path,
        line(first, 1, 9),
        HttpStatus.CONFLICT,
        "MEDICAL_PACKAGE_ITEM_EXISTS");
    assertError(
        HttpMethod.POST,
        path,
        line(newItem(), 1, 1),
        HttpStatus.CONFLICT,
        "MEDICAL_PACKAGE_ITEM_POSITION_TAKEN");
    assertError(
        HttpMethod.POST, path, line(retired, 1, 2), HttpStatus.CONFLICT, "MEDICAL_ITEM_INACTIVE");
    assertError(
        HttpMethod.POST,
        path,
        new MedicalPackageItemAddDto(UUID.randomUUID(), 1, 2),
        HttpStatus.NOT_FOUND,
        "MEDICAL_ITEM_NOT_FOUND");
    assertError(
        HttpMethod.POST,
        "/" + UUID.randomUUID() + "/items",
        line(first, 1, 2),
        HttpStatus.NOT_FOUND,
        "MEDICAL_PACKAGE_NOT_FOUND");
    assertError(
        HttpMethod.POST,
        path,
        new MedicalPackageItemAddDto(first.publicId(), 0, 2),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
    // none of the rejected calls rolled a version
    assertThat(versionNames(created.publicId())).containsExactly("v1.0");
  }

  @Test
  void updateItem_shouldRollNewVersion_andSkipItWhenNothingChanges() {
    MedicalItemResponseDto first = newItem();
    MedicalPackageResponseDto created = newPackage(first);
    String path = "/" + created.publicId() + "/items/" + first.publicId();

    PackageVersionDetailResponseDto changed =
        send(HttpMethod.PUT, path, new MedicalPackageItemUpdateDto(7, 4)).getBody().data();
    PackageVersionDetailResponseDto same =
        send(HttpMethod.PUT, path, new MedicalPackageItemUpdateDto(7, 4)).getBody().data();

    assertThat(changed.version()).isEqualTo("v1.1");
    assertThat(changed.medicalItems())
        .extracting("quantity", "position")
        .containsExactly(tuple(7, 4));
    assertThat(same.version()).isEqualTo("v1.1");
    assertThat(versionNames(created.publicId())).containsExactly("v1.1", "v1.0");
    assertError(
        HttpMethod.PUT,
        "/" + created.publicId() + "/items/" + UUID.randomUUID(),
        new MedicalPackageItemUpdateDto(1, 1),
        HttpStatus.NOT_FOUND,
        "MEDICAL_PACKAGE_ITEM_NOT_FOUND");
  }

  @Test
  void removeItem_shouldRollNewVersion_butRefuseTheLastItem() {
    MedicalItemResponseDto first = newItem();
    MedicalItemResponseDto second = newItem();
    MedicalPackageResponseDto created = newPackage(first);
    send(HttpMethod.POST, "/" + created.publicId() + "/items", line(second, 1, 2));

    ResponseEntity<ApiResult<PackageVersionDetailResponseDto>> response =
        send(HttpMethod.DELETE, "/" + created.publicId() + "/items/" + first.publicId(), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    PackageVersionDetailResponseDto data = response.getBody().data();
    assertThat(data.version()).isEqualTo("v1.2");
    assertThat(data.medicalItems())
        .extracting("medicalItemPublicId")
        .containsExactly(second.publicId());
    assertError(
        HttpMethod.DELETE,
        "/" + created.publicId() + "/items/" + second.publicId(),
        null,
        HttpStatus.CONFLICT,
        "MEDICAL_PACKAGE_ITEMS_REQUIRED");
    assertError(
        HttpMethod.DELETE,
        "/" + created.publicId() + "/items/" + UUID.randomUUID(),
        null,
        HttpStatus.NOT_FOUND,
        "MEDICAL_PACKAGE_ITEM_NOT_FOUND");
  }

  private List<String> versionNames(UUID packagePublicId) {
    return restTemplate
        .exchange(BASE + "/" + packagePublicId + "/versions", HttpMethod.GET, null, VERSIONS)
        .getBody()
        .data()
        .stream()
        .map(PackageVersionResponseDto::version)
        .toList();
  }

  private void assertNotFound(String url, String code) {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(url, HttpMethod.GET, null, ERROR);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", code);
  }
}
