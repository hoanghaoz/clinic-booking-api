package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicalPackageServiceTest {

  @Mock private MedicalPackageRepository medicalPackageRepository;
  @Mock private PackageVersionRepository packageVersionRepository;

  private MedicalPackageService service;

  @BeforeEach
  void setUp() {
    service = new MedicalPackageService(medicalPackageRepository, packageVersionRepository);
  }

  @Test
  void create_shouldSaveFirstVersionWithPackageCode() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    var dto =
        new MedicalPackageCreateDto(
            "HPV3",
            "Gói HPV 3 mũi",
            "mô tả",
            null,
            9,
            26,
            "hướng dẫn",
            PackageStatus.OPENING,
            new BigDecimal("8800000"),
            PackageClass.STANDARD,
            30,
            from);
    when(medicalPackageRepository.findByName("Gói HPV 3 mũi")).thenReturn(Optional.empty());

    MedicalPackageResponseDto response = service.createMedicalPackage(dto);

    ArgumentCaptor<PackageVersion> captor = ArgumentCaptor.forClass(PackageVersion.class);
    verify(packageVersionRepository).save(captor.capture());
    PackageVersion saved = captor.getValue();
    assertThat(saved.getVersion()).isEqualTo("v1.0");
    assertThat(saved.getCode()).isEqualTo("HPV3");
    assertThat(saved.getName()).isEqualTo("Gói HPV 3 mũi");
    assertThat(saved.getTarget()).isEqualTo(TargetGender.BOTH);
    assertThat(saved.getEffectiveFrom()).isEqualTo(from);
    assertThat(response.code()).isEqualTo("HPV3");
    assertThat(response.price()).isEqualByComparingTo("8800000");
  }

  @Test
  void create_shouldThrowConflict_whenNameAlreadyExists() {
    var dto = createDto("HPV3", "Gói HPV 3 mũi");
    when(medicalPackageRepository.findByName("Gói HPV 3 mũi"))
        .thenReturn(Optional.of(new MedicalPackage()));

    assertThatThrownBy(() -> service.createMedicalPackage(dto))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void update_shouldKeepOldValues_whenDtoFieldsNull() {
    UUID publicId = UUID.randomUUID();
    MedicalPackage pkg = packageOf("HPV3", "Gói HPV 3 mũi");
    PackageVersion old = versionOf(pkg, "v1.0", new BigDecimal("100"));
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findFirstByMedicalPackageOrderByEffectiveFromDesc(pkg))
        .thenReturn(Optional.of(old));

    var dto =
        new MedicalPackageUpdateDto(
            null, null, null, null, null, null, null, new BigDecimal("200"), null, null, null);
    MedicalPackageResponseDto response = service.updateMedicalPackage(dto, publicId);

    ArgumentCaptor<PackageVersion> captor = ArgumentCaptor.forClass(PackageVersion.class);
    verify(packageVersionRepository).save(captor.capture());
    PackageVersion saved = captor.getValue();
    assertThat(saved.getVersion()).isEqualTo("v1.1");
    assertThat(saved.getCode()).isEqualTo("HPV3");
    assertThat(saved.getName()).isEqualTo("Gói HPV 3 mũi");
    assertThat(saved.getUnitPrice()).isEqualByComparingTo("200");
    assertThat(saved.getConsultationMinutes()).isEqualTo(30);
    assertThat(old.getEffectiveTo()).isEqualTo(saved.getEffectiveFrom());
    assertThat(response.price()).isEqualByComparingTo("200");
  }

  @Test
  void update_shouldRenamePackage_whenNameProvided() {
    UUID publicId = UUID.randomUUID();
    MedicalPackage pkg = packageOf("HPV3", "Tên cũ");
    PackageVersion old = versionOf(pkg, "v1.0", new BigDecimal("100"));
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findFirstByMedicalPackageOrderByEffectiveFromDesc(pkg))
        .thenReturn(Optional.of(old));

    var dto =
        new MedicalPackageUpdateDto(
            "Tên mới", null, null, null, null, null, null, null, null, null, null);
    MedicalPackageResponseDto response = service.updateMedicalPackage(dto, publicId);

    assertThat(pkg.getName()).isEqualTo("Tên mới");
    assertThat(response.name()).isEqualTo("Tên mới");
  }

  @Test
  void update_shouldThrowNotFound_whenPackageMissing() {
    UUID publicId = UUID.randomUUID();
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.empty());
    var dto =
        new MedicalPackageUpdateDto(
            null, null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.updateMedicalPackage(dto, publicId))
        .isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "v1.0, v1.1",
    "v1.9, v1.10",
    "v1.10, v2.0",
    "v2.3, v2.4",
    "v9.10, v10.0",
  })
  void nextVersion_shouldIncrementMinorAndRollOverMajor(String last, String expected) {
    assertThat(MedicalPackageService.nextVersion(last)).isEqualTo(expected);
  }

  @Test
  void nextVersion_shouldReturnFirstVersion_whenNoPreviousVersion() {
    assertThat(MedicalPackageService.nextVersion(null)).isEqualTo("v1.0");
  }

  private static MedicalPackageCreateDto createDto(String code, String name) {
    return new MedicalPackageCreateDto(
        code,
        name,
        null,
        null,
        null,
        null,
        null,
        PackageStatus.OPENING,
        BigDecimal.TEN,
        PackageClass.STANDARD,
        30,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static MedicalPackage packageOf(String code, String name) {
    MedicalPackage pkg = new MedicalPackage();
    pkg.setCode(code);
    pkg.setName(name);
    return pkg;
  }

  private static PackageVersion versionOf(MedicalPackage pkg, String version, BigDecimal price) {
    PackageVersion v = new PackageVersion();
    v.setMedicalPackage(pkg);
    v.setCode(pkg.getCode());
    v.setName(pkg.getName());
    v.setVersion(version);
    v.setUnitPrice(price);
    v.setStatus(PackageStatus.OPENING);
    v.setPackageClass(PackageClass.STANDARD);
    v.setConsultationMinutes(30);
    v.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    return v;
  }
}
