package com.se100.clinic.catalog;

import static com.se100.clinic.catalog.CatalogDtos.*;

import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.shared.error.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicalPackageService {
  // Sort fields are PackageVersion properties: the list is built from current versions.
  public static final Set<String> SORTABLE_FIELDS =
      Set.of("code", "name", "status", "unitPrice", "createdAt");
  public static final Sort DEFAULT_SORT = Sort.by("name");

  private static final String FIRST_VERSION = "v1.0";
  private static final int MAX_MINOR_VERSION = 10;
  private final MedicalPackageRepository medicalPackageRepository;
  private final PackageVersionRepository packageVersionRepository;

  // TODO(specialties): imports needed when re-enabled: SpecialtyService (doctor module),
  // BusinessException (shared.error) and java.util.List.
  // TODO(specialties): private final PackageSpecialityRepository packageSpecialityRepository;
  // TODO(specialties): private final SpecialtyService specialtyService;

  private static PackageVersion createNewPackageVersion(
      PackageVersionFields dto, PackageVersion base, MedicalPackage medicalPackage) {
    PackageVersion version = new PackageVersion();
    version.setMedicalPackage(medicalPackage);
    version.setCode(medicalPackage.getCode());
    version.setVersion(nextVersion(base.getVersion()));
    version.setName(coalesce(dto.name(), base.getName()));
    version.setDescription(coalesce(dto.description(), base.getDescription()));
    version.setTarget(coalesce(dto.gender(), base.getTarget()));
    version.setMinAge(coalesce(dto.minAge(), base.getMinAge()));
    version.setMaxAge(coalesce(dto.maxAge(), base.getMaxAge()));
    version.setInstruction(coalesce(dto.instruction(), base.getInstruction()));
    version.setStatus(coalesce(dto.status(), base.getStatus()));
    version.setUnitPrice(coalesce(dto.price(), base.getUnitPrice()));
    version.setPackageClass(coalesce(dto.packageClass(), base.getPackageClass()));
    version.setConsultationMinutes(
        coalesce(dto.consultationMinutes(), base.getConsultationMinutes()));
    version.setEffectiveFrom(coalesce(dto.effectiveFrom(), Instant.now()));
    return version;
  }

  private static <T> T coalesce(T value, T fallback) {
    return value != null ? value : fallback;
  }

  /** Validate search request. */
  private static void validateSearchRequest(MedicalPackageSearchRequestDto request) {
    List<FieldViolation> violations = new ArrayList<>();
    if (request.minAge() != null
        && request.maxAge() != null
        && request.minAge() > request.maxAge()) {
      violations.add(new FieldViolation("minAge", "minAge phải nhỏ hơn hoặc bằng maxAge"));
    }
    if (request.minPrice() != null
        && request.maxPrice() != null
        && request.minPrice().compareTo(request.maxPrice()) > 0) {
      violations.add(new FieldViolation("minPrice", "minPrice phải nhỏ hơn hoặc bằng maxPrice"));
    }
    if (!violations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR, "Tham số tìm kiếm không hợp lệ", violations);
    }
  }

  /**
   * Builds the WHERE clause of the list query from whichever filters were supplied. Only the
   * current version of each package is matched, so every package appears exactly once.
   */
  private static Specification<PackageVersion> matching(MedicalPackageSearchRequestDto request) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("effectiveTo")));
      if (request.keyword() != null && !request.keyword().isBlank()) {
        String pattern = "%" + escapeLike(request.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("code")), pattern, '\\')));
      }
      if (request.status() != null) {
        predicates.add(cb.equal(root.get("status"), request.status()));
      }
      if (request.minPrice() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), request.minPrice()));
      }
      if (request.maxPrice() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), request.maxPrice()));
      }

      if (request.gender() != null) {
        predicates.add(
            request.gender() == TargetGender.BOTH
                ? cb.equal(root.get("target"), TargetGender.BOTH)
                : root.get("target").in(request.gender(), TargetGender.BOTH));
      }
      if (request.packageClass() != null) {
        predicates.add(cb.equal(root.get("packageClass"), request.packageClass()));
      }

      if (request.minAge() != null) {
        predicates.add(
            cb.or(
                cb.isNull(root.get("maxAge")),
                cb.greaterThanOrEqualTo(root.get("maxAge"), request.minAge())));
      }

      if (request.maxAge() != null) {
        predicates.add(
            cb.or(
                cb.isNull(root.get("minAge")),
                cb.lessThanOrEqualTo(root.get("minAge"), request.maxAge())));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Make user input literal in a LIKE pattern: {@code %} and {@code _} are wildcards otherwise. */
  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  // TODO(specialties): enable together with the specialtyIds field in CatalogDtos.
  // A package must belong to at least one active specialty. Specialties live in the doctor
  // module, so they are only checked through SpecialtyService (NOT_FOUND if the id is unknown).
  // private List<Long> validateSpecialties(List<Long> specialtyIds) {
  //   if (specialtyIds == null || specialtyIds.isEmpty()) {
  //     throw new BusinessException(
  //         CatalogErrorCode.MEDICAL_PACKAGE_SPECIALTY_REQUIRED,
  //         "A package must belong to at least one specialty");
  //   }
  //   List<Long> distinctIds = specialtyIds.stream().distinct().toList();
  //   for (Long specialtyId : distinctIds) {
  //     if (!specialtyService.getById(specialtyId).active()) {
  //       throw new ConflictException(
  //           CatalogErrorCode.MEDICAL_PACKAGE_SPECIALTY_INACTIVE,
  //           "Specialty id=" + specialtyId + " is inactive");
  //     }
  //   }
  //   return distinctIds;
  // }
  //
  // private void savePackageSpecialties(MedicalPackage medicalPackage, List<Long> specialtyIds) {
  //   List<PackageSpeciality> links =
  //       specialtyIds.stream()
  //           .map(
  //               specialtyId -> {
  //                 PackageSpeciality link = new PackageSpeciality();
  //                 link.setId(new PackageSpecialityId(medicalPackage.getId(), specialtyId));
  //                 link.setMedicalPackage(medicalPackage);
  //                 return link;
  //               })
  //           .toList();
  //   packageSpecialityRepository.saveAll(links);
  // }
  //
  // private List<Long> findSpecialtyIds(MedicalPackage medicalPackage) {
  //   return packageSpecialityRepository.findAllByMedicalPackage(medicalPackage).stream()
  //       .map(link -> link.getId().getSpecialtyId())
  //       .toList();
  // }
  //

  /** {@code null} -> v1.0; v1.0 -> v1.1; ... v1.10 -> v2.0. */
  static String nextVersion(String lastVersion) {
    if (lastVersion == null) {
      return FIRST_VERSION;
    }
    int dot = lastVersion.lastIndexOf('.');
    int major = Integer.parseInt(lastVersion.substring(1, dot));
    int minor = Integer.parseInt(lastVersion.substring(dot + 1)) + 1;
    if (minor > MAX_MINOR_VERSION) {
      return "v" + (major + 1) + ".0";
    }
    return "v" + major + "." + minor;
  }

  /**
   * @param pageable already validated by {@code PageParams#toPageable}
   */
  @Transactional(readOnly = true)
  public Page<MedicalPackageResponseDto> list(
      MedicalPackageSearchRequestDto request, Pageable pageable) {
    validateSearchRequest(request);
    return packageVersionRepository
        .findAll(matching(request), pageable)
        .map(version -> MedicalPackageResponseDto.from(version.getMedicalPackage(), version));
  }

  @Transactional
  public MedicalPackageResponseDto createMedicalPackage(MedicalPackageCreateDto dto) {
    if (medicalPackageRepository.findByCode(dto.code()).isPresent()) {
      throw new ConflictException("Medical package already exists");
    }
    // TODO(specialties): List<Long> specialtyIds = validateSpecialties(dto.specialtyIds());
    MedicalPackage medicalPackage = new MedicalPackage();
    medicalPackage.setCode(dto.code());
    medicalPackage.setName(dto.name());
    medicalPackageRepository.save(medicalPackage);

    PackageVersion packageVersion =
        createNewPackageVersion(dto, new PackageVersion(), medicalPackage);
    packageVersionRepository.save(packageVersion);
    // TODO(specialties): savePackageSpecialties(medicalPackage, specialtyIds);
    return MedicalPackageResponseDto.from(medicalPackage, packageVersion);
  }

  @Transactional
  public MedicalPackageResponseDto updateMedicalPackage(
      MedicalPackageUpdateDto dto, UUID packagePublicId) {
    MedicalPackage medicalPackage =
        medicalPackageRepository
            .findByPublicId(packagePublicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        CatalogErrorCode.MEDICAL_PACKAGE_NOT_FOUND, "Package not found"));
    PackageVersion currentVersion =
        packageVersionRepository
            .findFirstByMedicalPackageOrderByEffectiveFromDesc(medicalPackage)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        CatalogErrorCode.MEDICAL_PACKAGE_VERSION_NOT_FOUND,
                        "Package version not found"));

    if (dto.name() != null) {
      medicalPackage.setName(dto.name());
    }
    PackageVersion newVersion = createNewPackageVersion(dto, currentVersion, medicalPackage);

    // old version ends exactly when the new one starts: no gap, no overlap
    currentVersion.setEffectiveTo(newVersion.getEffectiveFrom());
    packageVersionRepository.save(newVersion);

    // TODO(specialties): pass findSpecialtyIds(medicalPackage) to the response
    return MedicalPackageResponseDto.from(medicalPackage, newVersion);
  }
}
