package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateBatchDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.shared.error.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Real Postgres: checks the unique name constraint and the all-or-nothing batch. */
class MedicalItemServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MedicalItemService medicalItemService;
  @Autowired private MedicalItemRepository medicalItemRepository;

  private static MedicalItemCreateDto dto(String name, MedicalItemType type) {
    return new MedicalItemCreateDto(name, type, new BigDecimal("120000"), null, null);
  }

  private static String tag() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void createBatch_shouldPersistEveryItem() {
    String tag = tag();

    List<MedicalItemResponseDto> created =
        medicalItemService.createBatchMedicalItems(
            new MedicalItemCreateBatchDto(
                List.of(
                    dto("Blood " + tag, MedicalItemType.LAB),
                    dto("X-ray " + tag, MedicalItemType.IMAGING))));

    assertThat(created).hasSize(2);
    assertThat(medicalItemRepository.existsByName("Blood " + tag)).isTrue();
    assertThat(medicalItemRepository.existsByName("X-ray " + tag)).isTrue();
  }

  @Test
  void createBatch_shouldPersistNothing_whenOneItemFailsInTheDatabase() {
    String tag = tag();

    // A null type bypasses Bean Validation here, so Postgres rejects it on flush.
    assertThatThrownBy(
            () ->
                medicalItemService.createBatchMedicalItems(
                    new MedicalItemCreateBatchDto(
                        List.of(
                            dto("Good " + tag, MedicalItemType.LAB), dto("Broken " + tag, null)))))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(medicalItemRepository.existsByName("Good " + tag)).isFalse();
    assertThat(medicalItemRepository.existsByName("Broken " + tag)).isFalse();
  }

  @Test
  void create_shouldThrowConflict_whenNameAlreadyStored() {
    String tag = tag();
    medicalItemService.createMedicalItem(dto("Dup " + tag, MedicalItemType.CLINICAL));

    assertThatThrownBy(
            () ->
                medicalItemService.createMedicalItem(
                    dto(" Dup " + tag + " ", MedicalItemType.CLINICAL)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void database_shouldRejectDuplicateName_evenWithoutServiceCheck() {
    String tag = tag();
    medicalItemService.createMedicalItem(dto("Raw " + tag, MedicalItemType.OTHER));

    MedicalItem duplicate = new MedicalItem();
    duplicate.setName("Raw " + tag);
    duplicate.setType(MedicalItemType.OTHER);
    duplicate.setPrice(BigDecimal.ONE);

    assertThatThrownBy(() -> medicalItemRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
