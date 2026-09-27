package com.se100.clinic.doctor;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import com.se100.clinic.shared.ConflictException;
import com.se100.clinic.shared.NotFoundException;
import java.util.List;
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
      throw new ConflictException("Mã chuyên khoa đã tồn tại: " + request.code());
    }
    Specialty specialty = new Specialty(request.code(), request.name(), request.description());
    return SpecialtyResponse.from(specialtyRepository.save(specialty));
  }

  @Transactional(readOnly = true)
  public List<SpecialtyResponse> list() {
    return specialtyRepository.findAll().stream().map(SpecialtyResponse::from).toList();
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
        .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa với id=" + id));
  }
}
