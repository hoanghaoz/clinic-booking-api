package com.se100.clinic.employee;

import com.se100.clinic.employee.enums.EmployeeStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** <b>PUBLIC</b> — the entry point other modules use to resolve clinic staff members. */
@Service
public class StaffService {

  private final StaffRepository staffRepository;

  StaffService(StaffRepository staffRepository) {
    this.staffRepository = staffRepository;
  }

  /**
   * The id of the staff member behind a login account, only while they are WORKING. Empty for
   * non-staff accounts and for staff who stopped working.
   */
  @Transactional(readOnly = true)
  public Optional<Long> findWorkingStaffIdByUserId(Long userId) {
    return staffRepository.findByUserIdAndStatus(userId, EmployeeStatus.WORKING).map(Staff::getId);
  }
}
