package com.se100.clinic.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.shared.error.GlobalExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Pins the schedule HTTP envelope and validation without requiring a database. */
@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {
  @Mock private ScheduleService schedules;
  @Mock private ScheduleAvailabilityService availability;
  @Mock private AuthenticatedUserService users;
  @Mock private DoctorSchedulingService doctors;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ScheduleController(schedules, availability, users, doctors))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns201LocationAndScheduleEnvelope() throws Exception {
    UUID id = UUID.randomUUID();
    when(schedules.create(any()))
        .thenReturn(
            new ScheduleResponse(
                id,
                11L,
                12L,
                13L,
                14L,
                15L,
                "MORNING",
                LocalDate.of(2026, 11, 3),
                Instant.parse("2026-11-03T01:00:00Z"),
                Instant.parse("2026-11-03T05:00:00Z"),
                12,
                8,
                ScheduleStatus.OPEN));

    mvc.perform(
            post("/api/v1/doctor-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"doctorId":11,"specialtyId":12,"clinicId":13,"roomId":14,
                     "shiftId":15,"workDate":"2026-11-03","generalCapacity":12,
                     "specialistCapacity":8}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/doctor-schedules/" + id))
        .andExpect(jsonPath("$.data.publicId").value(id.toString()))
        .andExpect(jsonPath("$.data.generalCapacity").value(12))
        .andExpect(jsonPath("$.meta").doesNotExist());
  }

  @Test
  void invalidAssignmentReturns400BeforeReachingService() throws Exception {
    mvc.perform(
            post("/api/v1/doctor-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"doctorId":-1,"specialtyId":12,"clinicId":13,"roomId":14,
                     "shiftId":15,"workDate":"2026-11-03","generalCapacity":0,
                     "specialistCapacity":8}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(2));
    verifyNoInteractions(schedules);
  }
}
