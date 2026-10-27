package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Real Postgres: checks that creating a package and rolling it over to a new version persist. */
class MedicalPackageServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MedicalPackageService medicalPackageService;
  @Autowired private MedicalItemService medicalItemService;

  /** One persisted item to put in a package, named uniquely so reruns never clash. */
  private MedicalPackageItemAddListDto oneItem() {
    var item =
        medicalItemService.createMedicalItem(
            new MedicalItemCreateDto(
                "Item " + UUID.randomUUID(), MedicalItemType.LAB, BigDecimal.TEN, null, null));
    return new MedicalPackageItemAddListDto(
        List.of(new MedicalPackageItemAddDto(item.publicId(), 1, 1)));
  }

  // TODO(specialties): also @Autowired SpecialtyService, create two specialties, pass their ids
  // in the create dto, and assert response.specialtyIds() after create and after update (the
  // composite-key PackageSpeciality rows must persist and survive the version rollover).

  @Test
  void createThenUpdate_shouldPersistNextVersion() {
    String tag = UUID.randomUUID().toString().substring(0, 8);

    MedicalPackageResponseDto created =
        medicalPackageService.createMedicalPackage(
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
                oneItem(),
                Instant.parse("2026-01-01T00:00:00Z")));

    MedicalPackageResponseDto updated =
        medicalPackageService.updateMedicalPackage(
            new MedicalPackageUpdateDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("2000000"),
                null,
                null,
                null,
                null),
            created.publicId());

    assertThat(updated.publicId()).isEqualTo(created.publicId());
    assertThat(updated.code()).isEqualTo("PKG" + tag);
    assertThat(updated.price()).isEqualByComparingTo("2000000");
  }

  @Test
  void list_shouldReturnOneRowPerPackage_usingCurrentVersionOnly() {
    String tag = tag();
    MedicalPackageResponseDto rolledOver = newPackage("A" + tag, PackageStatus.OPENING);
    newPackage("B" + tag, PackageStatus.CLOSING);
    // v1.0 -> v1.1: the package must still appear once, with the new price
    medicalPackageService.updateMedicalPackage(
        new MedicalPackageUpdateDto(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("2000000"),
            null,
            null,
            null,
            null),
        rolledOver.publicId());

    var pageable = PageRequest.of(0, 10, Sort.by("name"));
    Page<MedicalPackageResponseDto> all =
        medicalPackageService.list(search(tag, null, null, null, null, null, null, null), pageable);
    Page<MedicalPackageResponseDto> closing =
        medicalPackageService.list(
            search(tag, PackageStatus.CLOSING, null, null, null, null, null, null), pageable);

    assertThat(all.getTotalElements()).isEqualTo(2);
    assertThat(all.getContent())
        .extracting(MedicalPackageResponseDto::code)
        .containsExactly("PKGA" + tag, "PKGB" + tag);
    assertThat(all.getContent().get(0).price()).isEqualByComparingTo("2000000");
    assertThat(closing.getContent())
        .extracting(MedicalPackageResponseDto::code)
        .containsExactly("PKGB" + tag);
  }

  @Test
  void list_shouldFilterByGender_returningApplicablePackages() {
    String tag = tag();
    newPackage("A" + tag, TargetGender.MALE, null, null, PackageClass.STANDARD, "1000000");
    newPackage("B" + tag, TargetGender.FEMALE, null, null, PackageClass.STANDARD, "1000000");
    newPackage("C" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "1000000");

    assertThat(codes(tag, TargetGender.MALE, null, null, null, null, null))
        .containsExactly("PKGA" + tag, "PKGC" + tag);
    assertThat(codes(tag, TargetGender.FEMALE, null, null, null, null, null))
        .containsExactly("PKGB" + tag, "PKGC" + tag);
    assertThat(codes(tag, TargetGender.BOTH, null, null, null, null, null))
        .containsExactly("PKGC" + tag);
  }

  @Test
  void list_shouldFilterByAgeOverlap_treatingNullBoundsAsUnbounded() {
    String tag = tag();
    newPackage("A" + tag, TargetGender.BOTH, 0, 5, PackageClass.STANDARD, "1000000");
    newPackage("B" + tag, TargetGender.BOTH, 18, 60, PackageClass.STANDARD, "1000000");
    newPackage("C" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "1000000");

    assertThat(codes(tag, null, null, 30, 40, null, null))
        .containsExactly("PKGB" + tag, "PKGC" + tag);
    assertThat(codes(tag, null, null, 3, 10, null, null))
        .containsExactly("PKGA" + tag, "PKGC" + tag);
    // only one bound given
    assertThat(codes(tag, null, null, 70, null, null, null)).containsExactly("PKGC" + tag);
    assertThat(codes(tag, null, null, null, 2, null, null))
        .containsExactly("PKGA" + tag, "PKGC" + tag);
  }

  @Test
  void list_shouldFilterByPriceRange_inclusive() {
    String tag = tag();
    newPackage("A" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "1000000");
    newPackage("B" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "2000000");
    newPackage("C" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "3000000");

    var two = new BigDecimal("2000000");
    var three = new BigDecimal("3000000");
    assertThat(codes(tag, null, null, null, null, two, three))
        .containsExactly("PKGB" + tag, "PKGC" + tag);
    assertThat(codes(tag, null, null, null, null, new BigDecimal("2500000"), null))
        .containsExactly("PKGC" + tag);
    assertThat(codes(tag, null, null, null, null, null, new BigDecimal("1000000")))
        .containsExactly("PKGA" + tag);
  }

  @Test
  void list_shouldFilterByPackageClass_andMatchKeywordByCodeIgnoringCase() {
    String tag = tag();
    newPackage("A" + tag, TargetGender.BOTH, null, null, PackageClass.STANDARD, "1000000");
    newPackage("B" + tag, TargetGender.BOTH, null, null, PackageClass.ADVANCED, "1000000");

    assertThat(codes(tag, null, PackageClass.ADVANCED, null, null, null, null))
        .containsExactly("PKGB" + tag);
    // keyword is a code, upper-cased: the match must be case-insensitive and include code
    assertThat(codes(("PKGA" + tag).toUpperCase(), null, null, null, null, null, null))
        .containsExactly("PKGA" + tag);
  }

  private static String tag() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static MedicalPackageSearchRequestDto search(
      String keyword,
      PackageStatus status,
      TargetGender gender,
      PackageClass packageClass,
      Integer minAge,
      Integer maxAge,
      BigDecimal minPrice,
      BigDecimal maxPrice) {
    return new MedicalPackageSearchRequestDto(
        keyword, status, gender, packageClass, minAge, maxAge, minPrice, maxPrice);
  }

  /** Package codes matching the filters, ordered by name (A, B, C ...). */
  private List<String> codes(
      String keyword,
      TargetGender gender,
      PackageClass packageClass,
      Integer minAge,
      Integer maxAge,
      BigDecimal minPrice,
      BigDecimal maxPrice) {
    var request = search(keyword, null, gender, packageClass, minAge, maxAge, minPrice, maxPrice);
    return medicalPackageService
        .list(request, PageRequest.of(0, 20, Sort.by("name")))
        .map(MedicalPackageResponseDto::code)
        .getContent();
  }

  private MedicalPackageResponseDto newPackage(String suffix, PackageStatus status) {
    return newPackage(
        suffix, status, TargetGender.BOTH, null, null, PackageClass.STANDARD, "1000000");
  }

  private MedicalPackageResponseDto newPackage(
      String suffix,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      PackageClass packageClass,
      String price) {
    return newPackage(suffix, PackageStatus.OPENING, gender, minAge, maxAge, packageClass, price);
  }

  private MedicalPackageResponseDto newPackage(
      String suffix,
      PackageStatus status,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      PackageClass packageClass,
      String price) {
    return medicalPackageService.createMedicalPackage(
        new MedicalPackageCreateDto(
            "PKG" + suffix,
            "Package " + suffix,
            null,
            gender,
            minAge,
            maxAge,
            null,
            status,
            new BigDecimal(price),
            packageClass,
            30,
            oneItem(),
            Instant.parse("2026-01-01T00:00:00Z")));
  }
}
