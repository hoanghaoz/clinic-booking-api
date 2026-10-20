package com.se100.clinic.catalog;

import static com.se100.clinic.catalog.CatalogDtos.*;

import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.FieldViolation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicalItemService {
  private final MedicalItemRepository medicalItemRepository;

  private static MedicalItem toEntity(MedicalItemCreateDto request) {
    MedicalItem medicalItem = new MedicalItem();
    medicalItem.setName(normalizedName(request));
    medicalItem.setDescription(request.description());
    medicalItem.setPrice(request.price());
    medicalItem.setInstruction(request.instruction());
    medicalItem.setType(request.medicalItemType());
    return medicalItem;
  }

  /** The name as stored and compared: names are unique after trimming surrounding whitespace. */
  private static String normalizedName(MedicalItemCreateDto request) {
    return request.name().strip();
  }

  @Transactional
  public MedicalItemResponseDto createMedicalItem(MedicalItemCreateDto request) {
    String name = normalizedName(request);
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

  /**
   * Set.add returns false for a name already seen, which pinpoints the repeated element. A Set of
   * the DTOs themselves would not work: a record's equals compares every field, not just the name.
   */
  private static Set<String> collectNamesOrReject(List<MedicalItemCreateDto> items) {
    Set<String> names = new HashSet<>();
    List<FieldViolation> violations = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      if (!names.add(normalizedName(items.get(i)))) {
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
}
