package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.doctor.DoctorDtos.CreateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.DoctorResponse;
import com.se100.clinic.doctor.DoctorDtos.DoctorSpecialtyResponse;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorStatusRequest;
import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.BusinessException;
import com.se100.clinic.shared.ErrorCode;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

  @Mock private DoctorRepository doctorRepository;
  @Mock private SpecialtyRepository specialtyRepository;

  private DoctorService doctorService;

  @BeforeEach
  void setUp() {
    doctorService = new DoctorService(doctorRepository, specialtyRepository);
  }

  private static Specialty specialty(long id, String code, boolean active) {
    Specialty specialty = new Specialty(code, "Khoa " + code, null);
    ReflectionTestUtils.setField(specialty, "id", id);
    specialty.setActive(active);
    return specialty;
  }

  private static Doctor doctor(long id, Specialty... specialties) {
    Doctor doctor = new Doctor(10L + id, "BS" + id, "Bác sĩ " + id, "ThS");
    doctor.setId(id);
    for (Specialty specialty : specialties) {
      doctor.assignSpecialty(specialty);
    }
    return doctor;
  }

  private static CreateDoctorRequest createRequest(List<Long> specialtyIds) {
    return new CreateDoctorRequest(5L, "BS001", "Nguyễn Văn A", "ThS.BS", specialtyIds);
  }

  /** What Spring throws when Postgres rejects a write, including the parsed constraint name. */
  private static DataIntegrityViolationException dbViolation(String sqlState, String constraint) {
    return new DataIntegrityViolationException(
        "violation",
        new ConstraintViolationException(
            "violation", new SQLException("violation", sqlState), constraint));
  }

  private static void assertFailsWith(ErrorCode expected, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }

  // ---- create ------------------------------------------------------------------------------

  @Test
  void create_shouldSaveWorkingDoctorWithSpecialties() {
    Specialty noi = specialty(1, "NOI", true);
    Specialty tim = specialty(2, "TIM", true);
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(tim, noi));
    when(doctorRepository.saveAndFlush(any(Doctor.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    DoctorResponse response = doctorService.create(createRequest(List.of(1L, 2L, 1L)));

    assertThat(response.code()).isEqualTo("BS001");
    assertThat(response.userId()).isEqualTo(5L);
    assertThat(response.status()).isEqualTo(EmployeeStatus.WORKING);
    assertThat(response.specialties())
        .extracting(DoctorSpecialtyResponse::code)
        .containsExactly("NOI", "TIM");
  }

  @Test
  void create_shouldRejectDuplicateCode_beforeSaving() {
    when(doctorRepository.existsByCode("BS001")).thenReturn(true);

    assertFailsWith(
        DoctorErrorCode.DOCTOR_CODE_EXISTS, () -> doctorService.create(createRequest(List.of(1L))));
    verify(doctorRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldRejectAccountAlreadyLinkedToAnotherDoctor() {
    when(doctorRepository.existsByUserId(5L)).thenReturn(true);

    assertFailsWith(
        DoctorErrorCode.DOCTOR_USER_ALREADY_LINKED,
        () -> doctorService.create(createRequest(List.of(1L))));
    verify(doctorRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldReportMissingSpecialtyIds() {
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(1, "NOI", true)));

    assertFailsWith(
        SpecialtyErrorCode.SPECIALTY_NOT_FOUND,
        () -> doctorService.create(createRequest(List.of(1L, 99L))));
    assertThatThrownBy(() -> doctorService.create(createRequest(List.of(1L, 99L))))
        .hasMessageContaining("99");
    verify(doctorRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldRejectInactiveSpecialty() {
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(1, "NOI", false)));

    assertFailsWith(
        SpecialtyErrorCode.SPECIALTY_INACTIVE,
        () -> doctorService.create(createRequest(List.of(1L))));
  }

  @Test
  void create_shouldTranslateUserForeignKeyViolation_toUserNotFound() {
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(1, "NOI", true)));
    when(doctorRepository.saveAndFlush(any(Doctor.class)))
        .thenThrow(dbViolation("23503", "fk_doctors_user_id"));

    assertFailsWith(
        DoctorErrorCode.DOCTOR_USER_NOT_FOUND,
        () -> doctorService.create(createRequest(List.of(1L))));
  }

  @Test
  void create_shouldTranslateUniqueViolations_whenRaceBypassedExistsChecks() {
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(1, "NOI", true)));
    when(doctorRepository.saveAndFlush(any(Doctor.class)))
        .thenThrow(dbViolation("23505", "uq_doctors_code"))
        .thenThrow(dbViolation("23505", "doctors_user_id_key"));

    assertFailsWith(
        DoctorErrorCode.DOCTOR_CODE_EXISTS, () -> doctorService.create(createRequest(List.of(1L))));
    assertFailsWith(
        DoctorErrorCode.DOCTOR_USER_ALREADY_LINKED,
        () -> doctorService.create(createRequest(List.of(1L))));
  }

  @Test
  void create_shouldRethrowUnknownViolations_untranslated() {
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(1, "NOI", true)));
    var other = dbViolation("23505", "some_other_constraint");
    when(doctorRepository.saveAndFlush(any(Doctor.class))).thenThrow(other);

    assertThatThrownBy(() -> doctorService.create(createRequest(List.of(1L)))).isSameAs(other);
  }

  // ---- list / get --------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void list_shouldReturnMappedPageAndPassPageableThrough() {
    Pageable pageable = PageRequest.of(1, 5);
    when(doctorRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(doctor(1, specialty(1, "NOI", true))), pageable, 6));

    var page = doctorService.list("bs", 1L, EmployeeStatus.WORKING, pageable);

    assertThat(page.getContent()).extracting(DoctorResponse::code).containsExactly("BS1");
    assertThat(page.getTotalElements()).isEqualTo(6);
    verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void getById_shouldThrowNotFound_whenMissing() {
    when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

    assertFailsWith(DoctorErrorCode.DOCTOR_NOT_FOUND, () -> doctorService.getById(99L));
  }

  // ---- update ------------------------------------------------------------------------------

  @Test
  void update_shouldReplaceSpecialtyList_andRenameDoctor() {
    Specialty noi = specialty(1, "NOI", true);
    Specialty tim = specialty(2, "TIM", true);
    Specialty nhi = specialty(3, "NHI", true);
    Doctor existing = doctor(1, noi, tim);
    when(doctorRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(tim, nhi));

    DoctorResponse response =
        doctorService.update(1L, new UpdateDoctorRequest("Tên mới", "TS.BS", List.of(2L, 3L)));

    assertThat(response.name()).isEqualTo("Tên mới");
    assertThat(response.degree()).isEqualTo("TS.BS");
    assertThat(response.specialties())
        .extracting(DoctorSpecialtyResponse::code)
        .containsExactlyInAnyOrder("TIM", "NHI");
    verify(doctorRepository).flush();
  }

  @Test
  void update_shouldKeepAlreadyAssignedInactiveSpecialty_butRejectAddingOne() {
    Specialty oldInactive = specialty(1, "NOI", false);
    Specialty newInactive = specialty(2, "TIM", false);
    Doctor existing = doctor(1, oldInactive);
    when(doctorRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(specialtyRepository.findAllById(any()))
        .thenReturn(List.of(oldInactive))
        .thenReturn(List.of(oldInactive, newInactive));

    assertThatCode(() -> doctorService.update(1L, new UpdateDoctorRequest("A", "BS", List.of(1L))))
        .doesNotThrowAnyException();
    assertFailsWith(
        SpecialtyErrorCode.SPECIALTY_INACTIVE,
        () -> doctorService.update(1L, new UpdateDoctorRequest("A", "BS", List.of(1L, 2L))));
  }

  @Test
  void update_shouldTranslateScheduleForeignKey_toSpecialtyInUse() {
    Specialty noi = specialty(1, "NOI", true);
    Specialty tim = specialty(2, "TIM", true);
    when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor(1, noi, tim)));
    when(specialtyRepository.findAllById(any())).thenReturn(List.of(noi));
    doThrow(dbViolation("23503", "fk_doctor_schedules_doctor_id_specialty_id"))
        .when(doctorRepository)
        .flush();

    assertFailsWith(
        DoctorErrorCode.DOCTOR_SPECIALTY_IN_USE,
        () -> doctorService.update(1L, new UpdateDoctorRequest("A", "BS", List.of(1L))));
  }

  // ---- status / requireAssignable ----------------------------------------------------------

  @Test
  void changeStatus_shouldUpdateStatus() {
    Doctor existing = doctor(1, specialty(1, "NOI", true));
    when(doctorRepository.findById(1L)).thenReturn(Optional.of(existing));

    DoctorResponse response =
        doctorService.changeStatus(1L, new UpdateDoctorStatusRequest(EmployeeStatus.NOT_WORKING));

    assertThat(response.status()).isEqualTo(EmployeeStatus.NOT_WORKING);
    assertThat(existing.isWorking()).isFalse();
  }

  @Test
  void requireAssignable_shouldPass_forWorkingDoctorWithThatSpecialty() {
    when(doctorRepository.findById(1L))
        .thenReturn(Optional.of(doctor(1, specialty(1, "NOI", true))));

    assertThatCode(() -> doctorService.requireAssignable(1L, 1L)).doesNotThrowAnyException();
  }

  @Test
  void requireAssignable_shouldRejectNotWorkingDoctor() {
    Doctor existing = doctor(1, specialty(1, "NOI", true));
    existing.setStatus(EmployeeStatus.NOT_WORKING);
    when(doctorRepository.findById(1L)).thenReturn(Optional.of(existing));

    assertFailsWith(
        DoctorErrorCode.DOCTOR_NOT_WORKING, () -> doctorService.requireAssignable(1L, 1L));
  }

  @Test
  void requireAssignable_shouldRejectSpecialtyTheDoctorDoesNotHave() {
    when(doctorRepository.findById(1L))
        .thenReturn(Optional.of(doctor(1, specialty(1, "NOI", true))));

    assertFailsWith(
        DoctorErrorCode.DOCTOR_SPECIALTY_NOT_ASSIGNED,
        () -> doctorService.requireAssignable(1L, 2L));
  }
}
