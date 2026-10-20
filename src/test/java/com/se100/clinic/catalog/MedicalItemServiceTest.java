package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateBatchDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.FieldViolation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicalItemServiceTest {

  @Mock private MedicalItemRepository medicalItemRepository;
  @InjectMocks private MedicalItemService service;

  private static MedicalItemCreateDto dto(String name) {
    return new MedicalItemCreateDto(name, MedicalItemType.LAB, new BigDecimal("50000"), null, null);
  }

  @Test
  void create_shouldTrimNameAndMapFields() {
    when(medicalItemRepository.existsByName("Xét nghiệm máu")).thenReturn(false);

    MedicalItemResponseDto result = service.createMedicalItem(dto("  Xét nghiệm máu  "));

    assertThat(result.name()).isEqualTo("Xét nghiệm máu");
    assertThat(result.medicalItemType()).isEqualTo(MedicalItemType.LAB);
    assertThat(result.price()).isEqualByComparingTo("50000");
    verify(medicalItemRepository).saveAndFlush(any(MedicalItem.class));
  }

  @Test
  void create_shouldThrowConflict_whenNameAlreadyExists() {
    when(medicalItemRepository.existsByName("Siêu âm")).thenReturn(true);

    assertThatThrownBy(() -> service.createMedicalItem(dto("Siêu âm")))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((ConflictException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_ITEM_EXISTS);
    verify(medicalItemRepository, never()).saveAndFlush(any());
  }

  @Test
  void createBatch_shouldSaveAllAndKeepRequestOrder() {
    when(medicalItemRepository.findAllByNameIn(any())).thenReturn(List.of());

    List<MedicalItemResponseDto> result =
        service.createBatchMedicalItems(
            new MedicalItemCreateBatchDto(List.of(dto("A"), dto(" B "), dto("C"))));

    assertThat(result).extracting(MedicalItemResponseDto::name).containsExactly("A", "B", "C");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MedicalItem>> saved = ArgumentCaptor.forClass(List.class);
    verify(medicalItemRepository).saveAllAndFlush(saved.capture());
    assertThat(saved.getValue()).hasSize(3);
  }

  @Test
  void createBatch_shouldPointAtRepeatedName_whenDuplicateInsideRequest() {
    var request = new MedicalItemCreateBatchDto(List.of(dto("A"), dto("B"), dto(" A ")));

    assertThatThrownBy(() -> service.createBatchMedicalItems(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR);
              assertThat(e.getViolations())
                  .extracting(FieldViolation::field)
                  .containsExactly("items[2].name");
            });
    verify(medicalItemRepository, never()).saveAllAndFlush(anyList());
  }

  @Test
  void createBatch_shouldThrowConflict_whenSomeNamesAlreadyExist() {
    MedicalItem existing = new MedicalItem();
    existing.setName("B");
    when(medicalItemRepository.findAllByNameIn(any())).thenReturn(List.of(existing));
    var request = new MedicalItemCreateBatchDto(List.of(dto("A"), dto("B")));

    assertThatThrownBy(() -> service.createBatchMedicalItems(request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("B")
        .extracting(e -> ((ConflictException) e).getErrorCode())
        .isEqualTo(CatalogErrorCode.MEDICAL_ITEM_EXISTS);
    verify(medicalItemRepository, never()).saveAllAndFlush(anyList());
  }
}
