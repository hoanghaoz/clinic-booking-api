package com.se100.clinic.doctor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * package-private — CHỈ {@link SpecialtyService} (cùng package) được phép dùng repository này.
 * Không module nào khác được autowire {@code SpecialtyRepository} thẳng vào code của họ; nếu cố làm
 * vậy, code sẽ KHÔNG COMPILE (khác package không thấy được interface package-private) — đây chính
 * là cách Java ép buộc ranh giới module ở mức compiler, không chỉ là quy ước "nhớ đừng làm vậy" như
 * nhiều codebase NestJS/Express.
 *
 * <p>{@code JpaRepository<Specialty, Long>} tương đương {@code Repository<Specialty>} của TypeORM
 * hoặc {@code DbSet<Specialty>} của EF Core — Spring Data JPA tự sinh implementation lúc chạy
 * (proxy), bạn không cần viết class implement interface này.
 */
interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

  Optional<Specialty> findByCode(String code);

  boolean existsByCode(String code);
}
