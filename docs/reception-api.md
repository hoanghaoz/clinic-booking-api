# API tiếp nhận bệnh nhân

Module `reception` triển khai `REC-01`, `REC-02`, `REC-03`, `REC-04`. Quy ước chung về `ApiResult`, `ProblemDetail`, phân trang và thời gian nằm tại [API conventions](conventions/api-conventions.md).

## Ranh giới

- Sở hữu tra cứu tại quầy, kiểm tra điều kiện tiếp nhận, chuyển lịch hẹn `CONFIRMED → CHECKED_IN` (ghi `appointment_histories`), tạo phiếu tiếp nhận (`medical_visits`) và phân luồng hạng mục (`medical_orders`) vào phòng lâm sàng/cận lâm sàng.
- **Không** làm danh sách khám của bác sĩ (`REC-05`, thuộc module `examination`).
- Gọi lại service công khai của các module khác:
  - `PatientService` (`PAT-02`, `PAT-05`) cho tra cứu SĐT/CCCD, đọc hồ sơ và kiểm tra hạn thẻ.
  - `PatientMedicalHistoryService` (`PAT-03`) cho tiền sử bản thân, gia đình, dị ứng thuốc.
  - `AppointmentBookingService.findByQr` (`BOOK-04`) cho xác thực mã QR phiếu đặt lịch.
  - `StaffService` + `AuthenticatedUserService` cho xác thực nhân viên y tế đang làm việc (`WORKING`).

## Quy tắc nghiệp vụ đã chốt

- **Phạm vi cơ sở:** Nhân viên y tế chỉ tra cứu/tiếp nhận/điều phối được các lịch hẹn thuộc cơ sở mình được phân công trong `clinic_staffs`. Lịch hẹn cơ sở khác trả `403 CLINIC_ACCESS_DENIED`.
- **Trạng thái thẻ (PAT-05):**
  - `ACTIVE`: hợp lệ (`cardAlert = NONE`).
  - `EXPIRING_SOON` (còn ≤ 30 ngày) và `EXPIRED`: hiển thị cảnh báo (`cardAlert`, `cardWarningMessage`), **vẫn cho tiếp nhận**.
  - `REVOKED`: **chặn tiếp nhận** (`409 PATIENT_CARD_REVOKED`), cần mở lại thẻ trước.
- **Đúng giờ / đến sớm / quá giờ:**
  - `EARLY`: sớm hơn giờ hẹn > 30 phút; `ON_TIME`: trong vòng 30 phút trước giờ hẹn đến đúng giờ hẹn; `LATE`: sau giờ hẹn (`now > startAt`); `NOT_TODAY`: khác ngày hôm nay (giờ `Asia/Ho_Chi_Minh`).
  - Lịch hẹn trong ngày ở trạng thái `CONFIRMED` vẫn được tiếp nhận (kể cả sớm hoặc trễ) cho tới khi kết thúc ca (`shiftEndAt`). Khác ngày trả `409 APPOINTMENT_NOT_TODAY`, ca đã kết thúc trả `409 SHIFT_ALREADY_ENDED`.
- **Chống tiếp nhận trùng:** Khóa hàng `appointments` bằng `SELECT ... FOR UPDATE` kết hợp ràng buộc `UNIQUE(appointment_id)` trên `medical_visits`. Bấm tiếp nhận 2 lần hoặc 2 lễ tân bấm cùng lúc chỉ tạo tối đa 1 phiếu, yêu cầu còn lại trả `409 APPOINTMENT_ALREADY_CHECKED_IN`.
- **Hạng mục nhiều lần & phân luồng phòng (REC-04):**
  - Hạng mục trong gói có `quantity > 1` (ví dụ vắc-xin HPV 3 mũi) sinh đủ `occurrence_no = 1..quantity`, nhưng chỉ phân luồng và cấp số thứ tự hàng đợi cho **lần hiện tại (`occurrence_no = 1`)**; các lần tiếp theo giữ trạng thái `ORDERED`, `roomId = null`.
  - Hạng mục `CLINICAL` tự gán vào phòng khám của ca bác sĩ (`schedule.room_id`). Hạng mục cận lâm sàng (`LAB`, `IMAGING`, `VACCINATION`, `OTHER`) tự chọn phòng chức năng đang mở phù hợp nhất (ít người chờ nhất trong ngày) hoặc nhận `roomAssignments` do lễ tân chọn, và có thể điều phối lại qua endpoint `PUT .../routing`.

## Endpoint

Gốc: `/api/v1/receptions`. Tất cả yêu cầu JWT với vai trò `MEDICAL_STAFF` gắn hồ sơ `staffs.status = WORKING`.

| Method | Đường dẫn | Nội dung |
|---|---|---|
| GET | `/clinics` | Danh sách cơ sở nhân viên được phân công kèm các phòng đang mở |
| GET | `/lookup` | Tra cứu bệnh nhân & lịch hẹn bằng đúng 1 trong 4 tham số: `qr`, `appointmentCode`, `phone`, `identityNumber` |
| POST | `/` | Xác nhận tiếp nhận, tạo phiếu tiếp nhận + phân luồng phòng (`201 Created` + `Location`) |
| GET | `/` | Danh sách phiếu tiếp nhận trong ngày tại cơ sở (phân trang, lọc `date`, `keyword`, `status`) |
| GET | `/{visitPublicId}` | Chi tiết phiếu tiếp nhận và các hạng mục đã phân luồng |
| PUT | `/{visitPublicId}/orders/{orderPublicId}/routing` | Điều phối / đổi phòng chức năng cho một hạng mục |

### Ví dụ `POST /api/v1/receptions`

```json
{
  "appointmentPublicId": "9580cbde-8501-41b8-ab41-5b2f28a150ef",
  "clinicId": 1,
  "roomAssignments": [
    {
      "itemPublicId": "c102cfcb-c5bf-4f5e-b135-f8ba0fdabccf",
      "roomPublicId": "4c220a0d-3a6b-4cf1-b50a-2f08a6d87710"
    }
  ]
}
```

## Mã lỗi

| Mã | HTTP | Khi nào |
|---|---:|---|
| `STAFF_PROFILE_NOT_FOUND` | 403 | Tài khoản `MEDICAL_STAFF` chưa gắn hồ sơ nhân viên đang làm việc |
| `CLINIC_ACCESS_DENIED` | 403 | Lịch hẹn / phiếu thuộc cơ sở nhân viên không được phân công |
| `STAFF_CLINIC_REQUIRED` | 400 | Nhân viên thuộc nhiều cơ sở nhưng chưa truyền `clinicId` |
| `APPOINTMENT_NOT_FOUND` | 404 | Mã QR / mã đặt lịch / `appointmentPublicId` không tồn tại |
| `RECEPTION_VISIT_NOT_FOUND` | 404 | Không tìm thấy phiếu tiếp nhận |
| `RECEPTION_ORDER_NOT_FOUND` | 404 | Không tìm thấy hạng mục chỉ định thuộc phiếu |
| `RECEPTION_ROOM_NOT_FOUND` | 404 | Không tìm thấy phòng chức năng |
| `APPOINTMENT_ALREADY_CHECKED_IN` | 409 | Lịch hẹn đã được tiếp nhận (chống tiếp nhận lặp) |
| `APPOINTMENT_NOT_RECEIVABLE` | 409 | Lịch hẹn không ở trạng thái `CONFIRMED` (đã hủy, hoàn thành, bỏ hẹn, hết hạn...) |
| `APPOINTMENT_NOT_TODAY` | 409 | Lịch hẹn không thuộc ngày hôm nay |
| `SHIFT_ALREADY_ENDED` | 409 | Ca khám của lịch hẹn đã kết thúc |
| `PATIENT_CARD_REVOKED` | 409 | Thẻ bệnh nhân đã bị thu hồi |
| `CLINIC_NOT_OPEN` | 409 | Cơ sở hoặc phòng của ca đang ngừng hoạt động |
| `ROOM_NOT_IN_CLINIC`, `ROOM_NOT_OPEN` | 409 | Phòng điều phối không thuộc cùng cơ sở hoặc đang đóng |
| `RECEPTION_VISIT_CLOSED`, `ORDER_NOT_ROUTABLE` | 409 | Phiếu tiếp nhận hoặc hạng mục đã đóng / đang thực hiện |
