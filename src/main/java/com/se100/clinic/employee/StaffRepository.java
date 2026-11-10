package com.se100.clinic.employee;

import com.se100.clinic.employee.enums.EmployeeStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** package-private — only {@link StaffService} uses it. */
interface StaffRepository extends JpaRepository<Staff, Long> {

  Optional<Staff> findByUserIdAndStatus(Long userId, EmployeeStatus status);
}
