package com.se100.clinic.facility;

import com.se100.clinic.facility.enums.FacilityStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class FacilityDtos {
  private FacilityDtos() {}

  record SaveClinicRequest(
      @NotBlank @Size(max = 30) String code,
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 500) String address,
      @NotBlank @Size(max = 25) String phoneNumber,
      @Email @Size(max = 254) String email,
      @Size(max = 2000) String description) {}

  record SaveRoomRequest(
      @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name) {}

  record ChangeStatusRequest(@NotNull FacilityStatus status) {}

  record ClinicResponse(
      Long id,
      UUID publicId,
      String code,
      String name,
      String address,
      String phoneNumber,
      String email,
      String description,
      FacilityStatus status,
      Instant createdAt,
      Instant updatedAt) {
    static ClinicResponse from(Clinic clinic) {
      return new ClinicResponse(
          clinic.getId(),
          clinic.getPublicId(),
          clinic.getCode(),
          clinic.getName(),
          clinic.getAddress(),
          clinic.getPhoneNumber(),
          clinic.getEmail(),
          clinic.getDescription(),
          clinic.getStatus(),
          clinic.getCreatedAt(),
          clinic.getUpdatedAt());
    }
  }

  record RoomResponse(
      Long id,
      UUID publicId,
      Long clinicId,
      UUID clinicPublicId,
      String code,
      String name,
      FacilityStatus status,
      Instant createdAt,
      Instant updatedAt) {
    static RoomResponse from(Room room) {
      Clinic clinic = room.getClinic();
      return new RoomResponse(
          room.getId(),
          room.getPublicId(),
          clinic.getId(),
          clinic.getPublicId(),
          room.getCode(),
          room.getName(),
          room.getStatus(),
          room.getCreatedAt(),
          room.getUpdatedAt());
    }
  }
}
