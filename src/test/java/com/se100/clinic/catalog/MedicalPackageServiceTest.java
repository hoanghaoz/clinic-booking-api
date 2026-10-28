package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionResponseDto;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class MedicalPackageServiceTest {

  @Mock private MedicalPackageRepository medicalPackageRepository;
  @Mock private PackageVersionRepository packageVersionRepository;
  @Mock private MedicalItemRepository medicalItemRepository;
  @Mock private PackageVersionItemRepository packageVersionItemRepository;
  // TODO(specialties): @Mock private PackageSpecialityRepository packageSpecialityRepository;
  // @Mock private SpecialtyService specialtyService;

  private MedicalPackageService service;

  @BeforeEach
  void setUp() {
    // TODO(specialties): also pass packageSpecialityRepository and specialtyService
    service =
        new MedicalPackageService(
            medicalPackageRepository,
            packageVersionRepository,
            medicalItemRepository,
            packageVersionItemRepository);
  }

  @Test
  void create_shouldSaveFirstVersionWithPackageCode() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    MedicalItem item = new MedicalItem();
    item.setName("Blood test");
    UUID itemId = item.getPublicId();
    var dto =
        new MedicalPackageCreateDto(
            "HPV3",
            "HPV 3-dose package",
            "description",
            null,
            9,
            26,
            "instruction",
            PackageStatus.OPENING,
            new BigDecimal("8800000"),
            PackageClass.STANDARD,
            30,
            new MedicalPackageItemAddListDto(List.of(new MedicalPackageItemAddDto(itemId, 2, 1))),
            from);
    when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.empty());
    when(medicalItemRepository.findAllByPublicIdIn(any())).thenReturn(List.of(item));
    // TODO(specialties): when(specialtyService.getById(1L)).thenReturn(specialty(1L, true));
    // when(specialtyService.getById(2L)).thenReturn(specialty(2L, true));

    MedicalPackageResponseDto response = service.createMedicalPackage(dto);

    ArgumentCaptor<PackageVersion> captor = ArgumentCaptor.forClass(PackageVersion.class);
    verify(packageVersionRepository).save(captor.capture());
    PackageVersion saved = captor.getValue();
    assertThat(saved.getVersion()).isEqualTo("v1.0");
    assertThat(saved.getCode()).isEqualTo("HPV3");
    assertThat(saved.getName()).isEqualTo("HPV 3-dose package");
    assertThat(saved.getTarget()).isEqualTo(TargetGender.BOTH);
    assertThat(saved.getEffectiveFrom()).isEqualTo(from);
    assertThat(response.code()).isEqualTo("HPV3");
    assertThat(response.price()).isEqualByComparingTo("8800000");
    // TODO(specialties): assertThat(response.specialtyIds()).containsExactly(1L, 2L);
    // ArgumentCaptor<List<PackageSpeciality>> linkCaptor = ArgumentCaptor.captor();
    // verify(packageSpecialityRepository).saveAll(linkCaptor.capture());
    // assertThat(linkCaptor.getValue())
    // .extracting(link -> link.getId().getSpecialtyId())
    // .containsExactly(1L, 2L);
  }

  // TODO(specialties): @Test
  // void create_shouldThrowBusinessException_whenNoSpecialty() {
  //   var dto = createDto("HPV3", "HPV 3-dose package", List.of());
  //   when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.empty());
  //
  //   assertThatThrownBy(() -> service.createMedicalPackage(dto))
  //       .isInstanceOf(BusinessException.class);
  //   verify(medicalPackageRepository, never()).save(any());
  // }
  //
  // @Test
  // void create_shouldThrowNotFound_whenSpecialtyMissing() {
  //   var dto = createDto("HPV3", "HPV 3-dose package", List.of(99L));
  //   when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.empty());
  //   when(specialtyService.getById(99L)).thenThrow(new NotFoundException("Not found"));
  //
  //   assertThatThrownBy(() -> service.createMedicalPackage(dto))
  //       .isInstanceOf(NotFoundException.class);
  //   verify(medicalPackageRepository, never()).save(any());
  // }
  //
  // @Test
  // void create_shouldThrowConflict_whenSpecialtyInactive() {
  //   var dto = createDto("HPV3", "HPV 3-dose package", List.of(5L));
  //   when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.empty());
  //   when(specialtyService.getById(5L)).thenReturn(specialty(5L, false));
  //
  //   assertThatThrownBy(() -> service.createMedicalPackage(dto))
  //       .isInstanceOf(ConflictException.class);
  //   verify(medicalPackageRepository, never()).save(any());
  // }

  @Test
  void create_shouldThrowConflict_whenCodeAlreadyExists() {
    var dto = createDto("HPV3", "HPV 3-dose package");
    when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.of(new MedicalPackage()));

    assertThatThrownBy(() -> service.createMedicalPackage(dto))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((ConflictException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_PACKAGE_EXISTS);
  }

  @Test
  void update_shouldKeepOldValues_whenDtoFieldsNull() {
    UUID publicId = UUID.randomUUID();
    MedicalPackage pkg = packageOf("HPV3", "HPV 3-dose package");
    PackageVersion old = versionOf(pkg, "v1.0", new BigDecimal("100"));
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findFirstByMedicalPackageOrderByEffectiveFromDesc(pkg))
        .thenReturn(Optional.of(old));

    var dto =
        new MedicalPackageUpdateDto(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("200"),
            null,
            null,
            null,
            null);
    MedicalPackageResponseDto response = service.updateMedicalPackage(dto, publicId);

    ArgumentCaptor<PackageVersion> captor = ArgumentCaptor.forClass(PackageVersion.class);
    verify(packageVersionRepository).save(captor.capture());
    PackageVersion saved = captor.getValue();
    assertThat(saved.getVersion()).isEqualTo("v1.1");
    assertThat(saved.getCode()).isEqualTo("HPV3");
    assertThat(saved.getName()).isEqualTo("HPV 3-dose package");
    assertThat(saved.getUnitPrice()).isEqualByComparingTo("200");
    assertThat(saved.getConsultationMinutes()).isEqualTo(30);
    assertThat(old.getEffectiveTo()).isEqualTo(saved.getEffectiveFrom());
    assertThat(response.price()).isEqualByComparingTo("200");
  }

  @Test
  void update_shouldRenamePackage_whenNameProvided() {
    UUID publicId = UUID.randomUUID();
    MedicalPackage pkg = packageOf("HPV3", "Old name");
    PackageVersion old = versionOf(pkg, "v1.0", new BigDecimal("100"));
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findFirstByMedicalPackageOrderByEffectiveFromDesc(pkg))
        .thenReturn(Optional.of(old));

    var dto =
        new MedicalPackageUpdateDto(
            "New name", null, null, null, null, null, null, null, null, null, null, null);
    MedicalPackageResponseDto response = service.updateMedicalPackage(dto, publicId);

    assertThat(pkg.getName()).isEqualTo("New name");
    assertThat(response.name()).isEqualTo("New name");
  }

  @Test
  void update_shouldThrowNotFound_whenPackageMissing() {
    UUID publicId = UUID.randomUUID();
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.empty());
    var dto =
        new MedicalPackageUpdateDto(
            null, null, null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.updateMedicalPackage(dto, publicId))
        .isInstanceOf(NotFoundException.class)
        .extracting(e -> ((NotFoundException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_PACKAGE_NOT_FOUND);
  }

  @Test
  void list_shouldMapVersionsToResponses_keepingPageMetadata() {
    MedicalPackage pkg = packageOf("HPV3", "HPV 3-dose package");
    PackageVersion version = versionOf(pkg, "v1.0", new BigDecimal("100"));
    Pageable pageable = PageRequest.of(0, 20);
    when(packageVersionRepository.findAll(
            ArgumentMatchers.<Specification<PackageVersion>>any(), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(version), pageable, 1));

    var request =
        new MedicalPackageSearchRequestDto(
            "hpv", PackageStatus.OPENING, null, null, null, null, null, null);

    Page<MedicalPackageResponseDto> page = service.list(request, pageable);

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent())
        .singleElement()
        .satisfies(
            dto -> {
              assertThat(dto.code()).isEqualTo("HPV3");
              assertThat(dto.name()).isEqualTo("HPV 3-dose package");
              assertThat(dto.price()).isEqualByComparingTo("100");
            });
  }

  @Test
  void list_shouldSortByUnitPrice_whenSortedByPrice() {
    var request =
        new MedicalPackageSearchRequestDto(null, null, null, null, null, null, null, null);
    var requested = PageRequest.of(1, 5, Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));
    var expected =
        PageRequest.of(1, 5, Sort.by(Sort.Order.desc("unitPrice"), Sort.Order.asc("id")));
    when(packageVersionRepository.findAll(
            ArgumentMatchers.<Specification<PackageVersion>>any(), eq(expected)))
        .thenReturn(new PageImpl<>(List.of(), expected, 0));

    service.list(request, requested);

    verify(packageVersionRepository)
        .findAll(ArgumentMatchers.<Specification<PackageVersion>>any(), eq(expected));
  }

  @Test
  void create_shouldThrowBusinessException_whenMinAgeGreaterThanMaxAge() {
    var dto =
        new MedicalPackageCreateDto(
            "HPV3",
            "HPV package",
            null,
            null,
            30,
            20,
            null,
            PackageStatus.OPENING,
            BigDecimal.TEN,
            PackageClass.STANDARD,
            30,
            new MedicalPackageItemAddListDto(
                List.of(new MedicalPackageItemAddDto(UUID.randomUUID(), 1, 1))),
            null);
    when(medicalPackageRepository.findByCode("HPV3")).thenReturn(Optional.empty());
    when(medicalItemRepository.findAllByPublicIdIn(any())).thenReturn(List.of(new MedicalItem()));

    assertThatThrownBy(() -> service.createMedicalPackage(dto))
        .isInstanceOf(BusinessException.class);
    verify(packageVersionRepository, never()).save(any());
  }

  @Test
  void list_shouldThrowBusinessException_whenMinAgeGreaterThanMaxAge() {
    var request = new MedicalPackageSearchRequestDto(null, null, null, null, 10, 5, null, null);

    assertThatThrownBy(() -> service.list(request, PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(packageVersionRepository);
  }

  @Test
  void list_shouldThrowBusinessException_whenMinPriceGreaterThanMaxPrice() {
    var request =
        new MedicalPackageSearchRequestDto(
            null, null, null, null, null, null, new BigDecimal("200"), new BigDecimal("100"));

    assertThatThrownBy(() -> service.list(request, PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(packageVersionRepository);
  }

  @Test
  void listVersions_shouldMarkOnlyOpenVersionAsCurrent() {
    MedicalPackage pkg = packageOf("HPV3", "HPV package");
    PackageVersion old = versionOf(pkg, "v1.0", BigDecimal.ONE);
    old.setEffectiveTo(Instant.parse("2026-02-01T00:00:00Z"));
    PackageVersion current = versionOf(pkg, "v1.1", BigDecimal.TEN);
    Pageable pageable = PageRequest.of(0, 20);
    when(medicalPackageRepository.findByPublicId(pkg.getPublicId())).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findAllByMedicalPackage(pkg, pageable))
        .thenReturn(new PageImpl<>(List.of(current, old), pageable, 2));

    Page<PackageVersionResponseDto> page = service.listVersions(pkg.getPublicId(), pageable);

    assertThat(page.getContent())
        .extracting(PackageVersionResponseDto::version, PackageVersionResponseDto::current)
        .containsExactly(tuple("v1.1", true), tuple("v1.0", false));
    assertThat(page.getContent().get(1).effectiveTo())
        .isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
  }

  @Test
  void listVersions_shouldThrowNotFound_whenPackageDoesNotExist() {
    UUID publicId = UUID.randomUUID();
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listVersions(publicId, PageRequest.of(0, 20)))
        .isInstanceOf(NotFoundException.class)
        .extracting(e -> ((NotFoundException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_PACKAGE_NOT_FOUND);
    verifyNoInteractions(packageVersionRepository);
  }

  @Test
  void getVersion_shouldReturnSnapshotItems_inStoredOrder() {
    MedicalPackage pkg = packageOf("HPV3", "HPV package");
    PackageVersion version = versionOf(pkg, "v1.0", BigDecimal.TEN);
    MedicalItem item = new MedicalItem();
    item.setName("Current name");
    PackageVersionItem versionItem = new PackageVersionItem();
    versionItem.setItem(item);
    versionItem.setItemNameSnapshot("Name at the time");
    versionItem.setQuantity(2);
    versionItem.setPosition(1);
    when(medicalPackageRepository.findByPublicId(pkg.getPublicId())).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findByMedicalPackageAndVersion(pkg, "v1.0"))
        .thenReturn(Optional.of(version));
    when(packageVersionItemRepository.findAllByPackageVersionIdOrderByPositionAsc(any()))
        .thenReturn(List.of(versionItem));

    PackageVersionDetailResponseDto dto = service.getVersion(pkg.getPublicId(), "v1.0");

    assertThat(dto.version()).isEqualTo("v1.0");
    assertThat(dto.current()).isTrue();
    assertThat(dto.medicalItems())
        .singleElement()
        .satisfies(
            item0 -> {
              assertThat(item0.medicalItemPublicId()).isEqualTo(item.getPublicId());
              assertThat(item0.name()).isEqualTo("Name at the time");
              assertThat(item0.quantity()).isEqualTo(2);
            });
  }

  @Test
  void getVersion_shouldThrowNotFound_whenVersionDoesNotExist() {
    MedicalPackage pkg = packageOf("HPV3", "HPV package");
    when(medicalPackageRepository.findByPublicId(pkg.getPublicId())).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findByMedicalPackageAndVersion(pkg, "v9.9"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getVersion(pkg.getPublicId(), "v9.9"))
        .isInstanceOf(NotFoundException.class)
        .extracting(e -> ((NotFoundException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_PACKAGE_VERSION_NOT_FOUND);
  }

  @Test
  void getMedicalPackage_shouldReturnCurrentVersionWithItems() {
    MedicalPackage pkg = packageOf("HPV3", "HPV package");
    PackageVersion current = versionOf(pkg, "v1.1", BigDecimal.TEN);
    when(medicalPackageRepository.findByPublicId(pkg.getPublicId())).thenReturn(Optional.of(pkg));
    when(packageVersionRepository.findFirstByMedicalPackageOrderByEffectiveFromDesc(pkg))
        .thenReturn(Optional.of(current));
    when(packageVersionItemRepository.findAllByPackageVersionIdOrderByPositionAsc(any()))
        .thenReturn(List.of());

    MedicalPackageDetailResponseDto dto = service.getMedicalPackage(pkg.getPublicId());

    assertThat(dto.publicId()).isEqualTo(pkg.getPublicId());
    assertThat(dto.version()).isEqualTo("v1.1");
    assertThat(dto.price()).isEqualByComparingTo("10");
    assertThat(dto.medicalItems()).isEmpty();
  }

  @Test
  void getMedicalPackage_shouldThrowNotFound_whenPackageDoesNotExist() {
    UUID publicId = UUID.randomUUID();
    when(medicalPackageRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMedicalPackage(publicId))
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
        null,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  // TODO(specialties): private static SpecialtyResponse specialty(Long id, boolean active) {
  //   Instant now = Instant.parse("2026-01-01T00:00:00Z");
  //   return new SpecialtyResponse(id, "SP" + id, "Specialty " + id, null, active, now, now);
  // }

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
