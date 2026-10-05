package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import com.se100.clinic.shared.ConflictException;
import com.se100.clinic.shared.NotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit test THUẦN — không khởi Spring context, không cần Docker/DB thật. Chạy trong vài mili-giây
 * bằng {@code ./gradlew test}.
 *
 * <p>{@code @Mock}/{@code @ExtendWith(MockitoExtension.class)} thay {@link SpecialtyRepository}
 * bằng 1 "test double" — tương đương {@code jest.mock()}/{@code createMock<Repository>()} trong
 * NestJS test, hoặc {@code Mock<ISpecialtyRepository>()} (Moq) trong .NET.
 */
@ExtendWith(MockitoExtension.class)
class SpecialtyServiceTest {

  @Mock private SpecialtyRepository specialtyRepository;

  private SpecialtyService specialtyService;

  @BeforeEach
  void setUp() {
    specialtyService = new SpecialtyService(specialtyRepository);
  }

  @Test
  void create_shouldSaveAndReturnResponse_whenCodeNotTaken() {
    var request =
        new CreateSpecialtyRequest("NOI_TONG_QUAT", "Nội tổng quát", "Khám nội tổng quát");
    when(specialtyRepository.existsByCode("NOI_TONG_QUAT")).thenReturn(false);
    Specialty saved = new Specialty("NOI_TONG_QUAT", "Nội tổng quát", "Khám nội tổng quát");
    when(specialtyRepository.saveAndFlush(any(Specialty.class))).thenReturn(saved);

    SpecialtyResponse response = specialtyService.create(request);

    assertThat(response.code()).isEqualTo("NOI_TONG_QUAT");
    assertThat(response.name()).isEqualTo("Nội tổng quát");
    assertThat(response.active()).isTrue();

    ArgumentCaptor<Specialty> captor = ArgumentCaptor.forClass(Specialty.class);
    verify(specialtyRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getCode()).isEqualTo("NOI_TONG_QUAT");
  }

  @Test
  void create_shouldThrowConflict_whenCodeAlreadyExists() {
    var request = new CreateSpecialtyRequest("NOI_TONG_QUAT", "Nội tổng quát", null);
    when(specialtyRepository.existsByCode("NOI_TONG_QUAT")).thenReturn(true);

    assertThatThrownBy(() -> specialtyService.create(request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("NOI_TONG_QUAT")
        .extracting(e -> ((ConflictException) e).getErrorCode())
        .isEqualTo(SpecialtyErrorCode.SPECIALTY_CODE_EXISTS);
    verify(specialtyRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldTranslateUniqueViolationToConflict_whenRaceBypassedExistsCheck() {
    var request = new CreateSpecialtyRequest("NOI_TONG_QUAT", "Nội tổng quát", null);
    when(specialtyRepository.existsByCode("NOI_TONG_QUAT")).thenReturn(false);
    when(specialtyRepository.saveAndFlush(any(Specialty.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key", new SQLException("duplicate key", "23505")));

    assertThatThrownBy(() -> specialtyService.create(request))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((ConflictException) e).getErrorCode())
        .isEqualTo(SpecialtyErrorCode.SPECIALTY_CODE_EXISTS);
  }

  @Test
  void create_shouldRethrowOtherIntegrityViolations_untranslated() {
    var request = new CreateSpecialtyRequest("NOI_TONG_QUAT", "Nội tổng quát", null);
    when(specialtyRepository.existsByCode("NOI_TONG_QUAT")).thenReturn(false);
    var notNullViolation =
        new DataIntegrityViolationException("null", new SQLException("null value", "23502"));
    when(specialtyRepository.saveAndFlush(any(Specialty.class))).thenThrow(notNullViolation);

    // Not a unique violation, so it must NOT be reported as "code exists" — the global handler maps
    // it by SQLSTATE instead.
    assertThatThrownBy(() -> specialtyService.create(request)).isSameAs(notNullViolation);
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_shouldReturnMappedPageAndPassPageableThrough() {
    Pageable pageable = PageRequest.of(2, 5);
    Page<Specialty> found =
        new PageImpl<>(List.of(new Specialty("NHI", "Nhi khoa", null)), pageable, 11);
    when(specialtyRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(found);

    Page<SpecialtyResponse> result = specialtyService.list("nhi", true, pageable);

    assertThat(result.getContent()).extracting(SpecialtyResponse::code).containsExactly("NHI");
    assertThat(result.getTotalElements()).isEqualTo(11);
    assertThat(result.getNumber()).isEqualTo(2);
    verify(specialtyRepository)
        .findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable));
  }

  @Test
  void getById_shouldThrowNotFound_whenIdDoesNotExist() {
    when(specialtyRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> specialtyService.getById(99L))
        .isInstanceOf(NotFoundException.class)
        .extracting(e -> ((NotFoundException) e).getErrorCode())
        .isEqualTo(SpecialtyErrorCode.SPECIALTY_NOT_FOUND);
  }

  @Test
  void update_shouldRenameExistingSpecialty() {
    Specialty existing = new Specialty("TIM_MACH", "Tim mạch", "cũ");
    when(specialtyRepository.findById(1L)).thenReturn(Optional.of(existing));

    SpecialtyResponse response =
        specialtyService.update(1L, new UpdateSpecialtyRequest("Tim mạch can thiệp", "mới"));

    assertThat(response.name()).isEqualTo("Tim mạch can thiệp");
    assertThat(response.description()).isEqualTo("mới");
    // Không verify save() ở đây — dirty checking của Hibernate xử lý việc lưu, xem comment
    // trong SpecialtyService#update().
  }

  @Test
  void deactivate_shouldSetActiveFalse() {
    Specialty existing = new Specialty("DA_LIEU", "Da liễu", null);
    when(specialtyRepository.findById(2L)).thenReturn(Optional.of(existing));

    specialtyService.deactivate(2L);

    assertThat(existing.isActive()).isFalse();
  }
}
