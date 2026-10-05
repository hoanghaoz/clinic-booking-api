package com.se100.clinic.doctor;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import com.se100.clinic.shared.ConflictException;
import com.se100.clinic.shared.DbErrors;
import com.se100.clinic.shared.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>PUBLIC</b> — đây là API DUY NHẤT module khác được phép gọi để thao tác với dữ liệu Chuyên
 * khoa. Không có "interface" tách riêng khỏi "implementation" kiểu {@code ISpecialtyService}/{@code
 * SpecialtyService : ISpecialtyService} như thường thấy trong ASP.NET Core — Spring không yêu cầu
 * điều đó, {@code @Service} + constructor injection là đủ để test bằng Mockito (mock thẳng class
 * này) và để Spring quản lý làm bean.
 *
 * <p>{@code @Transactional}: 1 method = 1 giao dịch DB, tự rollback nếu ném {@code
 * RuntimeException} — tương đương {@code @Transaction()} decorator của TypeORM hoặc {@code using
 * var transaction = ...} thủ công của EF Core, nhưng Spring làm việc này khai báo (declarative),
 * không cần tự mở/đóng transaction bằng tay.
 */
@Service
public class SpecialtyService {

  /**
   * Fields {@code GET /specialties} may be sorted by. They are API field names AND entity property
   * names (keep them identical), so only expose fields that are indexed or cheap to sort.
   */
  public static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "active", "createdAt");

  /** Used when the client sends no {@code sort}. */
  public static final Sort DEFAULT_SORT = Sort.by("name");

  private final SpecialtyRepository specialtyRepository;

  // Constructor injection: Spring tự tìm bean SpecialtyRepository và truyền vào đây khi khởi
  // tạo SpecialtyService — không cần @Autowired trên field, không cần Lombok
  // @RequiredArgsConstructor.
  // Giống constructor injection mặc định của NestJS (`constructor(private repo:
  // Repository<Specialty>)`).
  SpecialtyService(SpecialtyRepository specialtyRepository) {
    this.specialtyRepository = specialtyRepository;
  }

  @Transactional
  public SpecialtyResponse create(CreateSpecialtyRequest request) {
    if (specialtyRepository.existsByCode(request.code())) {
      throw codeExists(request.code());
    }
    Specialty specialty = new Specialty(request.code(), request.name(), request.description());
    try {
      // saveAndFlush: make the INSERT run now so a unique-constraint violation surfaces inside this
      // try block (not at commit). Two requests can both pass existsByCode above; the DB unique
      // constraint is the real guard, and we translate its violation into the same business code.
      return SpecialtyResponse.from(specialtyRepository.saveAndFlush(specialty));
    } catch (DataIntegrityViolationException e) {
      if (DbErrors.isUniqueViolation(e)) {
        throw codeExists(request.code());
      }
      throw e;
    }
  }

  /**
   * One page of specialties. Both filters are optional.
   *
   * @param keyword case-insensitive "contains" match on name or code; blank = no filter
   * @param active exact match; {@code null} = both active and inactive
   * @param pageable already validated by {@code PageParams#toPageable}
   */
  @Transactional(readOnly = true)
  public Page<SpecialtyResponse> list(String keyword, Boolean active, Pageable pageable) {
    return specialtyRepository
        .findAll(matching(keyword, active), pageable)
        .map(SpecialtyResponse::from);
  }

  @Transactional(readOnly = true)
  public SpecialtyResponse getById(Long id) {
    return SpecialtyResponse.from(findOrThrow(id));
  }

  @Transactional
  public SpecialtyResponse update(Long id, UpdateSpecialtyRequest request) {
    Specialty specialty = findOrThrow(id);
    specialty.rename(request.name(), request.description());
    return SpecialtyResponse.from(specialty);
    // Không cần gọi repository.save() ở đây: entity đang ở trạng thái "managed" trong
    // Persistence Context (vì được load trong cùng @Transactional), Hibernate tự phát hiện
    // thay đổi field lúc commit transaction (dirty checking) và tự UPDATE. Đây là khác biệt
    // lớn so với TypeORM/Prisma — ở đó bạn LUÔN phải gọi repo.save()/prisma.update() tường minh.
  }

  /**
   * Soft-delete: đánh dấu ngưng hoạt động thay vì xoá cứng, để giữ lịch sử tham chiếu (vd. bác sĩ
   * đã gán chuyên khoa này).
   */
  @Transactional
  public void deactivate(Long id) {
    Specialty specialty = findOrThrow(id);
    specialty.setActive(false);
  }

  private Specialty findOrThrow(Long id) {
    return specialtyRepository
        .findById(id)
        .orElseThrow(
            () ->
                new NotFoundException(
                    SpecialtyErrorCode.SPECIALTY_NOT_FOUND,
                    "Không tìm thấy chuyên khoa với id=" + id));
  }

  private static ConflictException codeExists(String code) {
    return new ConflictException(
        SpecialtyErrorCode.SPECIALTY_CODE_EXISTS, "Mã chuyên khoa đã tồn tại: " + code);
  }

  /** Builds the WHERE clause from whichever filters were supplied (none = match everything). */
  private static Specification<Specialty> matching(String keyword, Boolean active) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (keyword != null && !keyword.isBlank()) {
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("code")), pattern, '\\')));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Make user input literal in a LIKE pattern: {@code %} and {@code _} are wildcards otherwise. */
  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
