package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateBatchDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemUpdateDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.shared.error.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

  private MedicalItemResponseDto createItem(String name, MedicalItemType type, String price) {
    return medicalItemService.createMedicalItem(
        new MedicalItemCreateDto(name, type, new BigDecimal(price), null, null));
  }

  private List<String> searchNames(MedicalItemSearchRequestDto request, Sort sort) {
    return medicalItemService.searchMedicalItems(request, PageRequest.of(0, 50, sort)).stream()
        .map(MedicalItemResponseDto::name)
        .toList();
  }

  @Test
  void search_shouldMatchKeywordIgnoringCase_andTreatWildcardsLiterally() {
    String tag = tag();
    createItem("Siêu âm tim " + tag, MedicalItemType.IMAGING, "100");
    createItem("Xét nghiệm 100% " + tag, MedicalItemType.LAB, "100");
    createItem("Xét nghiệm 1000 " + tag, MedicalItemType.LAB, "100");

    assertThat(
            searchNames(
                new MedicalItemSearchRequestDto("SIÊU ÂM TIM " + tag, null, null, null, null),
                Sort.by("name")))
        .containsExactly("Siêu âm tim " + tag);
    // '%' is escaped, so only the name holding a literal "100%" matches.
    assertThat(
            searchNames(
                new MedicalItemSearchRequestDto("100% " + tag, null, null, null, null),
                Sort.by("name")))
        .containsExactly("Xét nghiệm 100% " + tag);
  }

  @Test
  void search_shouldFilterByTypeAndInclusivePriceRange_andSort() {
    String tag = tag();
    createItem("Low " + tag, MedicalItemType.LAB, "100");
    createItem("Mid " + tag, MedicalItemType.LAB, "200");
    createItem("High " + tag, MedicalItemType.LAB, "300");
    createItem("Scan " + tag, MedicalItemType.IMAGING, "200");

    var range =
        new MedicalItemSearchRequestDto(
            tag, MedicalItemType.LAB, new BigDecimal("100"), new BigDecimal("200"), null);
    assertThat(searchNames(range, Sort.by(Sort.Direction.DESC, "price")))
        .containsExactly("Mid " + tag, "Low " + tag);

    assertThat(
            searchNames(
                new MedicalItemSearchRequestDto(tag, MedicalItemType.IMAGING, null, null, null),
                Sort.by("name")))
        .containsExactly("Scan " + tag);
  }

  @Test
  void update_shouldPersistChanges_andRejectNameOfAnotherItem() {
    String tag = tag();
    MedicalItemResponseDto first = createItem("First " + tag, MedicalItemType.LAB, "100");
    createItem("Second " + tag, MedicalItemType.LAB, "100");

    MedicalItemResponseDto updated =
        medicalItemService.updateMedicalItem(
            first.publicId(),
            new MedicalItemUpdateDto(
                "  Renamed " + tag, MedicalItemType.IMAGING, new BigDecimal("250"), "d", "i"));

    assertThat(updated.name()).isEqualTo("Renamed " + tag);
    assertThat(medicalItemRepository.existsByName("Renamed " + tag)).isTrue();
    assertThat(medicalItemRepository.existsByName("First " + tag)).isFalse();
    assertThat(medicalItemRepository.findByPublicId(first.publicId()).orElseThrow().getPrice())
        .isEqualByComparingTo("250");
    // keeping its own name is fine, taking another item's name is not
    medicalItemService.updateMedicalItem(
        first.publicId(),
        new MedicalItemUpdateDto(
            "Renamed " + tag, MedicalItemType.LAB, BigDecimal.ONE, null, null));
    assertThatThrownBy(
            () ->
                medicalItemService.updateMedicalItem(
                    first.publicId(),
                    new MedicalItemUpdateDto(
                        "Second " + tag, MedicalItemType.LAB, BigDecimal.ONE, null, null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void deactivate_shouldKeepTheRow_andSearchFiltersByActive() {
    String tag = tag();
    MedicalItemResponseDto kept = createItem("Kept " + tag, MedicalItemType.LAB, "100");
    MedicalItemResponseDto retired = createItem("Retired " + tag, MedicalItemType.LAB, "100");

    medicalItemService.deactivateMedicalItem(retired.publicId());

    assertThat(searchNames(searchByActive(tag, true), Sort.by("name")))
        .containsExactly("Kept " + tag);
    assertThat(searchNames(searchByActive(tag, false), Sort.by("name")))
        .containsExactly("Retired " + tag);
    assertThat(searchNames(searchByActive(tag, null), Sort.by("name")))
        .containsExactly("Kept " + tag, "Retired " + tag);
    assertThat(medicalItemRepository.findByPublicId(kept.publicId()).orElseThrow().isActive())
        .isTrue();
  }

  private static MedicalItemSearchRequestDto searchByActive(String tag, Boolean active) {
    return new MedicalItemSearchRequestDto(tag, null, null, null, active);
  }
}
