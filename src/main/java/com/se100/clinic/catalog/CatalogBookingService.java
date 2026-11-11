package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import com.se100.clinic.doctor.SpecialtyService;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Catalog data used to select an appointment and snapshot its terms at confirmation. */
@Service
@RequiredArgsConstructor
public class CatalogBookingService {
  private static final Set<Integer> BOOKABLE_DURATIONS = Set.of(20, 30);

  private final MedicalPackageRepository packages;
  private final PackageVersionRepository versions;
  private final PackageSpecialityRepository packageSpecialties;
  private final SpecialtyService specialties;
  private final ServiceCatalogRepository services;

  @Transactional(readOnly = true)
  public Product packageForAvailability(UUID publicId) {
    return packageProduct(
        packages.findByPublicId(publicId).orElseThrow(CatalogBookingService::packageNotFound));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Product lockPackageForBooking(UUID publicId, String expectedVersion) {
    Product product =
        packageProduct(
            packages
                .findByPublicIdForBooking(publicId)
                .orElseThrow(CatalogBookingService::packageNotFound));
    if (!product.version().equals(expectedVersion)) {
      throw new ConflictException(
          CatalogErrorCode.MEDICAL_PACKAGE_VERSION_CHANGED,
          "Gói khám đã đổi phiên bản, vui lòng chọn lại");
    }
    return product;
  }

  @Transactional(readOnly = true)
  public Product serviceForAvailability(UUID publicId) {
    return serviceProduct(
        services.findByPublicId(publicId).orElseThrow(CatalogBookingService::serviceNotFound));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Product lockServiceForBooking(UUID publicId) {
    return serviceProduct(
        services
            .findForBookingByPublicId(publicId)
            .orElseThrow(CatalogBookingService::serviceNotFound));
  }

  @Transactional(readOnly = true)
  public Page<StandaloneService> listServices(Pageable pageable) {
    return services
        .findByDurationMinutesIn(BOOKABLE_DURATIONS, pageable)
        .map(
            s ->
                new StandaloneService(
                    s.getPublicId(),
                    s.getName(),
                    s.getPrice(),
                    s.getDurationMinutes(),
                    s.getTarget(),
                    s.getMinAge(),
                    s.getMaxAge()));
  }

  @Transactional(readOnly = true)
  public List<Long> packageSpecialties(UUID publicId) {
    MedicalPackage medicalPackage =
        packages.findByPublicId(publicId).orElseThrow(CatalogBookingService::packageNotFound);
    return specialtyIds(medicalPackage).stream().sorted().toList();
  }

  @Transactional
  public List<Long> replacePackageSpecialties(UUID publicId, List<Long> specialtyIds) {
    MedicalPackage medicalPackage =
        packages
            .findByPublicIdForUpdate(publicId)
            .orElseThrow(CatalogBookingService::packageNotFound);
    List<Long> requested = specialtyIds.stream().distinct().sorted().toList();
    for (Long id : requested) {
      if (!specialties.getById(id).active()) {
        throw new ConflictException(
            CatalogErrorCode.MEDICAL_PACKAGE_SPECIALTY_INACTIVE, "Chuyên khoa đã ngừng hoạt động");
      }
    }
    List<PackageSpeciality> existing = packageSpecialties.findAllByMedicalPackage(medicalPackage);
    Set<Long> existingIds =
        existing.stream().map(link -> link.getId().getSpecialtyId()).collect(Collectors.toSet());
    packageSpecialties.deleteAll(
        existing.stream()
            .filter(link -> !requested.contains(link.getId().getSpecialtyId()))
            .toList());
    for (Long id : requested) {
      if (!existingIds.contains(id)) {
        PackageSpeciality link = new PackageSpeciality();
        link.setId(new PackageSpecialityId(medicalPackage.getId(), id));
        link.setMedicalPackage(medicalPackage);
        packageSpecialties.save(link);
      }
    }
    return requested;
  }

  private Product packageProduct(MedicalPackage medicalPackage) {
    PackageVersion version =
        versions
            .findEffectiveVersion(medicalPackage, Instant.now())
            .orElseThrow(
                () ->
                    new ConflictException(
                        CatalogErrorCode.MEDICAL_PACKAGE_NOT_BOOKABLE,
                        "Gói khám chưa có phiên bản hiệu lực"));
    Set<Long> specialtyIds = specialtyIds(medicalPackage);
    if (version.getStatus() == PackageStatus.CLOSING
        || !BOOKABLE_DURATIONS.contains(version.getConsultationMinutes())
        || specialtyIds.isEmpty()) {
      throw new ConflictException(
          CatalogErrorCode.MEDICAL_PACKAGE_NOT_BOOKABLE,
          "Gói khám tạm ngưng hoặc chưa có chuyên khoa và thời lượng đặt lịch hợp lệ");
    }
    return new Product(
        version.getId(),
        null,
        version.getVersion(),
        version.getConsultationMinutes(),
        version.getUnitPrice(),
        version.getTarget(),
        version.getMinAge(),
        version.getMaxAge(),
        version.getInstruction(),
        specialtyIds);
  }

  private Set<Long> specialtyIds(MedicalPackage medicalPackage) {
    return packageSpecialties.findAllByMedicalPackage(medicalPackage).stream()
        .map(link -> link.getId().getSpecialtyId())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Product serviceProduct(MedicalService service) {
    if (!BOOKABLE_DURATIONS.contains(service.getDurationMinutes())) {
      throw new ConflictException(
          CatalogErrorCode.MEDICAL_SERVICE_NOT_BOOKABLE,
          "Dịch vụ không có thời lượng 20 hoặc 30 phút");
    }
    return new Product(
        null,
        service.getId(),
        null,
        service.getDurationMinutes(),
        service.getPrice(),
        service.getTarget(),
        service.getMinAge(),
        service.getMaxAge(),
        service.getInstruction(),
        Set.of());
  }

  private static NotFoundException packageNotFound() {
    return new NotFoundException(
        CatalogErrorCode.MEDICAL_PACKAGE_NOT_FOUND, "Không tìm thấy gói khám");
  }

  private static NotFoundException serviceNotFound() {
    return new NotFoundException(
        CatalogErrorCode.MEDICAL_SERVICE_NOT_FOUND, "Không tìm thấy dịch vụ");
  }

  public record Product(
      Long packageVersionId,
      Long serviceId,
      String version,
      int durationMinutes,
      BigDecimal price,
      TargetGender target,
      Integer minAge,
      Integer maxAge,
      String instruction,
      Set<Long> specialtyIds) {}

  public record StandaloneService(
      UUID publicId,
      String name,
      BigDecimal price,
      int durationMinutes,
      TargetGender target,
      Integer minAge,
      Integer maxAge) {}
}
