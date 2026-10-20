package com.se100.clinic.catalog;

import static com.se100.clinic.catalog.CatalogDtos.*;

import com.se100.clinic.shared.error.*;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicalItemService {

  public static final Set<String> SORTABLE_FIELDS = Set.of("name", "type", "price", "createdAt");
  public static final Sort DEFAULT_SORT = Sort.by("name");
  private final MedicalItemRepository medicalItemRepository;

  private static MedicalItem toEntity(MedicalItemCreateDto request) {
    MedicalItem medicalItem = new MedicalItem();
    medicalItem.setName(normalizedName(request.name()));
    medicalItem.setDescription(request.description());
    medicalItem.setPrice(request.price());
    medicalItem.setInstruction(request.instruction());
    medicalItem.setType(request.type());
    return medicalItem;
  }

  /** The name as stored and compared: names are unique after trimming surrounding whitespace. */
  private static String normalizedName(String request) {
    return request.strip();
  }

  /** Make user input literal in a LIKE pattern: {@code %} and {@code _} are wildcards otherwise. */
  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Specification<MedicalItem> matching(MedicalItemSearchRequestDto request) {
    return ((root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (request.keyword() != null && !request.keyword().isBlank()) {
        String pattern = "%" + escapeLike(request.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
      }

      if (request.minPrice() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("price"), request.minPrice()));
      }
      if (request.maxPrice() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("price"), request.maxPrice()));
      }

      if (request.type() != null) {
        predicates.add(cb.equal(root.get("type"), request.type()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    });
  }

  private static void validateRequest(MedicalItemSearchRequestDto request) {
    if (request.minPrice() != null
        && request.maxPrice() != null
        && request.minPrice().compareTo(request.maxPrice()) > 0) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Yêu cầu tìm kiếm không hợp lệ",
          List.of(new FieldViolation("minPrice", "minPrice phải nhỏ hơn hoặc bằng maxPrice")));
    }
  }

  /**
   * Set.add returns false for a name already seen, which pinpoints the repeated element. A Set of
   * the DTOs themselves would not work: a record's equals compares every field, not just the name.
   */
  private static Set<String> collectNamesOrReject(List<MedicalItemCreateDto> items) {
    Set<String> names = new HashSet<>();
    List<FieldViolation> violations = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      if (!names.add(normalizedName(items.get(i).name()))) {
        violations.add(
            new FieldViolation("items[" + i + "].name", "Tên hạng mục bị trùng trong danh sách"));
      }
    }
    if (!violations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR, "Danh sách hạng mục không hợp lệ", violations);
    }
    return names;
  }

  /** Turn a unique violation (a race past the pre-check) into the module code; rethrow others. */
  private static RuntimeException asNameConflict(
      DataIntegrityViolationException e, String message) {
    if (DbErrors.isUniqueViolation(e)) {
      return new ConflictException(CatalogErrorCode.MEDICAL_ITEM_EXISTS, message);
    }
    return e;
  }

  @Transactional(readOnly = true)
  public Page<MedicalItemResponseDto> searchMedicalItems(
      MedicalItemSearchRequestDto request, Pageable pageable) {
    validateRequest(request);
    var spec = matching(request);
    return medicalItemRepository.findAll(spec, pageable).map(MedicalItemResponseDto::from);
  }

  @Transactional
  public MedicalItemResponseDto createMedicalItem(MedicalItemCreateDto request) {
    String name = normalizedName(request.name());
    if (medicalItemRepository.existsByName(name)) {
      throw new ConflictException(
          CatalogErrorCode.MEDICAL_ITEM_EXISTS, "Hạng mục '" + name + "' đã tồn tại");
    }
    MedicalItem medicalItem = toEntity(request);
    try {
      // Flush now so a concurrent duplicate surfaces here and not at commit time.
      medicalItemRepository.saveAndFlush(medicalItem);
    } catch (DataIntegrityViolationException e) {
      throw asNameConflict(e, "Hạng mục '" + name + "' đã tồn tại");
    }
    return MedicalItemResponseDto.from(medicalItem);
  }

  /** All-or-nothing: any invalid or duplicate item rejects the whole batch. */
  @Transactional
  public List<MedicalItemResponseDto> createBatchMedicalItems(MedicalItemCreateBatchDto request) {
    Set<String> names = collectNamesOrReject(request.items());

    List<String> existing =
        medicalItemRepository.findAllByNameIn(names).stream().map(MedicalItem::getName).toList();
    if (!existing.isEmpty()) {
      throw new ConflictException(
          CatalogErrorCode.MEDICAL_ITEM_EXISTS,
          "Hạng mục đã tồn tại: " + String.join(", ", existing));
    }

    List<MedicalItem> medicalItems =
        request.items().stream().map(MedicalItemService::toEntity).toList();
    try {
      medicalItemRepository.saveAllAndFlush(medicalItems);
    } catch (DataIntegrityViolationException e) {
      throw asNameConflict(e, "Có hạng mục trùng tên với dữ liệu hiện có");
    }
    return medicalItems.stream().map(MedicalItemResponseDto::from).toList();
  }
}
