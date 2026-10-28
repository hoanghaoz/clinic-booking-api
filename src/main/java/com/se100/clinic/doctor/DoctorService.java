package com.se100.clinic.doctor;

import com.se100.clinic.doctor.DoctorDtos.CreateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.DoctorResponse;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorStatusRequest;
import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.ConflictException;
import com.se100.clinic.shared.DbErrors;
import com.se100.clinic.shared.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>PUBLIC</b> — Doctor management (RES-04, UC-11) and the only entry point other modules use to
 * ask about doctors (e.g. {@code schedule} calls {@link #requireAssignable} before assigning a
 * shift).
 *
 * <p>The doctor's login account is created by Admin in the {@code auth} module first; this service
 * only links to it by {@code userId}. Whether that id exists is enforced by the DB foreign key
 * {@code fk_doctors_user_id} and translated into {@code DOCTOR_USER_NOT_FOUND}.
 */
@Service
public class DoctorService {

  /** Fields {@code GET /doctors} may be sorted by (API names = entity property names). */
  public static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "status", "createdAt");

  public static final Sort DEFAULT_SORT = Sort.by("name");

  // Constraint names from the Flyway migrations; Postgres names inline UNIQUE as <table>_<col>_key.
  private static final String CODE_UNIQUE = "uq_doctors_code";
  private static final String USER_UNIQUE = "doctors_user_id_key";
  private static final String USER_FK = "fk_doctors_user_id";

  private final DoctorRepository doctorRepository;
  private final SpecialtyRepository specialtyRepository;

  DoctorService(DoctorRepository doctorRepository, SpecialtyRepository specialtyRepository) {
    this.doctorRepository = doctorRepository;
    this.specialtyRepository = specialtyRepository;
  }

  @Transactional
  public DoctorResponse create(CreateDoctorRequest request) {
    if (doctorRepository.existsByCode(request.code())) {
      throw codeExists(request.code());
    }
    if (doctorRepository.existsByUserId(request.userId())) {
      throw userAlreadyLinked(request.userId());
    }
    Doctor doctor = new Doctor(request.userId(), request.code(), request.name(), request.degree());
    for (Specialty specialty : loadSpecialties(request.specialtyIds()).values()) {
      requireActive(specialty);
      doctor.assignSpecialty(specialty);
    }
    try {
      // saveAndFlush so constraint violations surface here (see SpecialtyService#create).
      return DoctorResponse.from(doctorRepository.saveAndFlush(doctor));
    } catch (DataIntegrityViolationException e) {
      throw translateCreateViolation(e, request);
    }
  }

  /**
   * One page of doctors. All filters are optional and combined with AND.
   *
   * @param keyword case-insensitive "contains" match on name or code
   * @param specialtyId only doctors having this specialty
   * @param status exact match
   */
  @Transactional(readOnly = true)
  public Page<DoctorResponse> list(
      String keyword, Long specialtyId, EmployeeStatus status, Pageable pageable) {
    return doctorRepository
        .findAll(matching(keyword, specialtyId, status), pageable)
        .map(DoctorResponse::from);
  }

  @Transactional(readOnly = true)
  public DoctorResponse getById(Long id) {
    return DoctorResponse.from(findOrThrow(id));
  }

  /**
   * Updates name/degree and REPLACES the specialty list with {@code request.specialtyIds()}.
   * Specialties the doctor already has may stay even if they were deactivated since; newly added
   * ones must be active.
   */
  @Transactional
  public DoctorResponse update(Long id, UpdateDoctorRequest request) {
    Doctor doctor = findOrThrow(id);
    doctor.setName(request.name());
    doctor.setDegree(request.degree());

    Map<Long, Specialty> wanted = loadSpecialties(request.specialtyIds());
    // Diff instead of clear-and-re-add: Hibernate flushes inserts before deletes, so re-adding a
    // removed (doctor, specialty) pair in the same flush would hit the primary key.
    doctor.getSpecialities().stream()
        .map(link -> link.getSpecialty().getId())
        .filter(specialtyId -> !wanted.containsKey(specialtyId))
        .toList()
        .forEach(doctor::unassignSpecialty);
    for (Specialty specialty : wanted.values()) {
      if (!doctor.hasSpecialty(specialty.getId())) {
        requireActive(specialty);
        doctor.assignSpecialty(specialty);
      }
    }
    try {
      doctorRepository.flush();
    } catch (DataIntegrityViolationException e) {
      // The only FK a removed doctor_specialities row can break is doctor_schedules'.
      if (DbErrors.isForeignKeyViolation(e)) {
        throw new ConflictException(
            DoctorErrorCode.DOCTOR_SPECIALTY_IN_USE,
            "Không thể bỏ chuyên khoa đang được dùng trong lịch làm việc của bác sĩ");
      }
      throw e;
    }
    return DoctorResponse.from(doctor);
  }

  /**
   * WORKING / NOT_WORKING. A NOT_WORKING doctor stays in lists (filter by {@code status}) but is
   * rejected by {@link #requireAssignable}, so no new shift can be assigned.
   */
  @Transactional
  public DoctorResponse changeStatus(Long id, UpdateDoctorStatusRequest request) {
    Doctor doctor = findOrThrow(id);
    doctor.setStatus(request.status());
    return DoctorResponse.from(doctor);
  }

  /**
   * For other modules (e.g. {@code schedule}, UC-12 E1): checks that the doctor exists, is WORKING
   * and has the given specialty. Throws {@code DOCTOR_NOT_FOUND}, {@code DOCTOR_NOT_WORKING} or
   * {@code DOCTOR_SPECIALTY_NOT_ASSIGNED}.
   */
  @Transactional(readOnly = true)
  public void requireAssignable(Long doctorId, Long specialtyId) {
    Doctor doctor = findOrThrow(doctorId);
    if (!doctor.isWorking()) {
      throw new ConflictException(
          DoctorErrorCode.DOCTOR_NOT_WORKING,
          "Bác sĩ " + doctor.getCode() + " đang ngừng hoạt động");
    }
    if (!doctor.hasSpecialty(specialtyId)) {
      throw new ConflictException(
          DoctorErrorCode.DOCTOR_SPECIALTY_NOT_ASSIGNED,
          "Bác sĩ " + doctor.getCode() + " không thuộc chuyên khoa id=" + specialtyId);
    }
  }

  private Doctor findOrThrow(Long id) {
    return doctorRepository
        .findById(id)
        .orElseThrow(
            () ->
                new NotFoundException(
                    DoctorErrorCode.DOCTOR_NOT_FOUND, "Không tìm thấy bác sĩ với id=" + id));
  }

  /**
   * Requested specialties by id (duplicates ignored), or SPECIALTY_NOT_FOUND naming the missing.
   */
  private Map<Long, Specialty> loadSpecialties(Collection<Long> ids) {
    Set<Long> requested = new LinkedHashSet<>(ids);
    Map<Long, Specialty> found =
        specialtyRepository.findAllById(requested).stream()
            .collect(Collectors.toMap(Specialty::getId, Function.identity()));
    List<Long> missing = requested.stream().filter(id -> !found.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      throw new NotFoundException(
          SpecialtyErrorCode.SPECIALTY_NOT_FOUND, "Không tìm thấy chuyên khoa với id=" + missing);
    }
    return found;
  }

  private static void requireActive(Specialty specialty) {
    if (!specialty.isActive()) {
      throw new ConflictException(
          SpecialtyErrorCode.SPECIALTY_INACTIVE,
          "Chuyên khoa " + specialty.getCode() + " đã ngưng hoạt động");
    }
  }

  /** Two requests can both pass the exists checks; the DB constraints are the real guard. */
  private static RuntimeException translateCreateViolation(
      DataIntegrityViolationException e, CreateDoctorRequest request) {
    String constraint = DbErrors.constraintName(e);
    if (DbErrors.isUniqueViolation(e) && CODE_UNIQUE.equals(constraint)) {
      return codeExists(request.code());
    }
    if (DbErrors.isUniqueViolation(e) && USER_UNIQUE.equals(constraint)) {
      return userAlreadyLinked(request.userId());
    }
    if (DbErrors.isForeignKeyViolation(e) && USER_FK.equals(constraint)) {
      return new NotFoundException(
          DoctorErrorCode.DOCTOR_USER_NOT_FOUND,
          "Không tìm thấy tài khoản với id=" + request.userId());
    }
    return e;
  }

  private static ConflictException codeExists(String code) {
    return new ConflictException(
        DoctorErrorCode.DOCTOR_CODE_EXISTS, "Mã bác sĩ đã tồn tại: " + code);
  }

  private static ConflictException userAlreadyLinked(Long userId) {
    return new ConflictException(
        DoctorErrorCode.DOCTOR_USER_ALREADY_LINKED,
        "Tài khoản id=" + userId + " đã được gán cho một bác sĩ khác");
  }

  private static Specification<Doctor> matching(
      String keyword, Long specialtyId, EmployeeStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (keyword != null && !keyword.isBlank()) {
        String pattern =
            "%" + SpecialtyService.escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("code")), pattern, '\\')));
      }
      if (specialtyId != null) {
        // EXISTS subquery rather than a join, so a doctor is never duplicated across pages.
        Subquery<Long> hasSpecialty = query.subquery(Long.class);
        Root<DoctorSpeciality> link = hasSpecialty.from(DoctorSpeciality.class);
        hasSpecialty
            .select(link.get("id").get("specialtyId"))
            .where(
                cb.equal(link.get("doctor"), root),
                cb.equal(link.get("id").get("specialtyId"), specialtyId));
        predicates.add(cb.exists(hasSpecialty));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
