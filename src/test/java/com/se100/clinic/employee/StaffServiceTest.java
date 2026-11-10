package com.se100.clinic.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.se100.clinic.employee.enums.EmployeeStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit test: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

  @Mock private StaffRepository staffRepository;

  private StaffService staffService;

  @BeforeEach
  void setUp() {
    staffService = new StaffService(staffRepository);
  }

  @Test
  void findWorkingStaffIdByUserId_shouldReturnId_whenStaffIsWorking() {
    Staff staff = new Staff();
    staff.setId(4L);
    when(staffRepository.findByUserIdAndStatus(21L, EmployeeStatus.WORKING))
        .thenReturn(Optional.of(staff));

    assertThat(staffService.findWorkingStaffIdByUserId(21L)).contains(4L);
  }

  @Test
  void findWorkingStaffIdByUserId_shouldReturnEmpty_whenNoWorkingStaffForAccount() {
    when(staffRepository.findByUserIdAndStatus(99L, EmployeeStatus.WORKING))
        .thenReturn(Optional.empty());

    assertThat(staffService.findWorkingStaffIdByUserId(99L)).isEmpty();
  }
}
