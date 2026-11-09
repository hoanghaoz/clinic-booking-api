package com.se100.clinic.facility;

import com.se100.clinic.facility.FacilityDtos.ClinicResponse;
import com.se100.clinic.facility.FacilityDtos.RoomResponse;
import com.se100.clinic.facility.FacilityDtos.SaveClinicRequest;
import com.se100.clinic.facility.FacilityDtos.SaveRoomRequest;
import com.se100.clinic.facility.enums.FacilityStatus;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FacilityManagementService {
  private final ClinicRepository clinics;
  private final RoomRepository rooms;

  public Page<ClinicResponse> listClinics(
      String keyword, FacilityStatus status, Pageable pageable) {
    return clinics.findAll(matching(keyword, status, null), pageable).map(ClinicResponse::from);
  }

  public ClinicResponse getClinic(UUID publicId) {
    return ClinicResponse.from(requireClinic(publicId, false));
  }

  @Transactional
  public ClinicResponse createClinic(SaveClinicRequest request) {
    Clinic clinic = new Clinic();
    apply(clinic, request);
    clinic.setStatus(FacilityStatus.OPENING);
    return ClinicResponse.from(saveClinic(clinic));
  }

  @Transactional
  public ClinicResponse updateClinic(UUID publicId, SaveClinicRequest request) {
    Clinic clinic = requireClinic(publicId, true);
    apply(clinic, request);
    return ClinicResponse.from(saveClinic(clinic));
  }

  @Transactional
  public ClinicResponse changeClinicStatus(UUID publicId, FacilityStatus status) {
    Clinic clinic = requireClinic(publicId, true);
    if (status != clinic.getStatus()) {
      if (status == FacilityStatus.CLOSING
          && clinics.hasUpcomingSchedules(clinic.getId(), Instant.now())) {
        throw schedulesExist("Cơ sở");
      }
      clinic.setStatus(status);
      clinics.flush();
    }
    return ClinicResponse.from(clinic);
  }

  public Page<RoomResponse> listRooms(
      UUID clinicPublicId, String keyword, FacilityStatus status, Pageable pageable) {
    Long clinicId = requireClinic(clinicPublicId, false).getId();
    return rooms.findAll(matching(keyword, status, clinicId), pageable).map(RoomResponse::from);
  }

  public RoomResponse getRoom(UUID clinicPublicId, UUID roomPublicId) {
    Clinic clinic = requireClinic(clinicPublicId, false);
    return RoomResponse.from(requireRoom(clinic.getId(), roomPublicId, false));
  }

  @Transactional
  public RoomResponse createRoom(UUID clinicPublicId, SaveRoomRequest request) {
    Clinic clinic = requireClinic(clinicPublicId, true);
    Room room = new Room();
    room.setClinic(clinic);
    room.setStatus(clinic.getStatus());
    apply(room, request);
    return RoomResponse.from(saveRoom(room));
  }

  @Transactional
  public RoomResponse updateRoom(UUID clinicPublicId, UUID roomPublicId, SaveRoomRequest request) {
    Clinic clinic = requireClinic(clinicPublicId, true);
    Room room = requireRoom(clinic.getId(), roomPublicId, true);
    apply(room, request);
    return RoomResponse.from(saveRoom(room));
  }

  @Transactional
  public RoomResponse changeRoomStatus(
      UUID clinicPublicId, UUID roomPublicId, FacilityStatus status) {
    Clinic clinic = requireClinic(clinicPublicId, true);
    Room room = requireRoom(clinic.getId(), roomPublicId, true);
    if (status != room.getStatus()) {
      if (status == FacilityStatus.OPENING && clinic.getStatus() != FacilityStatus.OPENING) {
        throw new ConflictException(
            FacilityErrorCode.CLINIC_CLOSED, "Hãy mở cơ sở trước khi mở phòng");
      }
      if (status == FacilityStatus.CLOSING
          && rooms.hasUpcomingSchedules(room.getId(), Instant.now())) {
        throw schedulesExist("Phòng");
      }
      room.setStatus(status);
      rooms.flush();
    }
    return RoomResponse.from(room);
  }

  private Clinic requireClinic(UUID publicId, boolean lock) {
    return (lock ? clinics.findForUpdate(publicId) : clinics.findByPublicId(publicId))
        .orElseThrow(
            () ->
                new NotFoundException(FacilityErrorCode.CLINIC_NOT_FOUND, "Không tìm thấy cơ sở"));
  }

  private Room requireRoom(Long clinicId, UUID publicId, boolean lock) {
    return (lock
            ? rooms.findForUpdate(clinicId, publicId)
            : rooms.findByClinicIdAndPublicId(clinicId, publicId))
        .orElseThrow(
            () ->
                new NotFoundException(
                    FacilityErrorCode.FACILITY_ROOM_NOT_FOUND,
                    "Không tìm thấy phòng trong cơ sở này"));
  }

  private Clinic saveClinic(Clinic clinic) {
    try {
      return clinics.saveAndFlush(clinic);
    } catch (DataIntegrityViolationException error) {
      if ("clinics_code_key".equals(DbErrors.constraintName(error))) {
        throw new ConflictException(FacilityErrorCode.CLINIC_CODE_EXISTS, "Mã cơ sở đã tồn tại");
      }
      throw error;
    }
  }

  private Room saveRoom(Room room) {
    try {
      return rooms.saveAndFlush(room);
    } catch (DataIntegrityViolationException error) {
      if ("uk_rooms_clinic_code".equals(DbErrors.constraintName(error))) {
        throw new ConflictException(
            FacilityErrorCode.ROOM_CODE_EXISTS, "Mã phòng đã tồn tại trong cơ sở này");
      }
      throw error;
    }
  }

  private static ConflictException schedulesExist(String entity) {
    return new ConflictException(
        FacilityErrorCode.FACILITY_HAS_SCHEDULES,
        entity + " còn ca đang diễn ra hoặc sắp tới. Xử lý các ca trước khi ngừng hoạt động");
  }

  private static void apply(Clinic clinic, SaveClinicRequest request) {
    clinic.setCode(request.code().strip());
    clinic.setName(request.name().strip());
    clinic.setAddress(request.address().strip());
    clinic.setPhoneNumber(request.phoneNumber().strip());
    clinic.setEmail(blankToNull(request.email()));
    clinic.setDescription(blankToNull(request.description()));
  }

  private static void apply(Room room, SaveRoomRequest request) {
    room.setCode(request.code().strip());
    room.setName(request.name().strip());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static <T> Specification<T> matching(
      String keyword, FacilityStatus status, Long clinicId) {
    return (root, query, cb) -> {
      List<Predicate> filters = new ArrayList<>();
      if (clinicId != null) {
        filters.add(cb.equal(root.get("clinic").get("id"), clinicId));
      }
      if (status != null) {
        filters.add(cb.equal(root.get("status"), status));
      }
      if (keyword != null && !keyword.isBlank()) {
        String literal =
            keyword
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + literal + "%";
        filters.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("code")), pattern, '\\')));
      }
      return cb.and(filters.toArray(Predicate[]::new));
    };
  }
}
