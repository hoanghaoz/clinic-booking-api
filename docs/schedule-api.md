# API lịch làm việc bác sĩ

Module `schedule` triển khai SCH-01–SCH-05, FR-23, FR-24 và phần lịch/ca của FR-50. Quy ước chung về `ApiResult`, `ProblemDetail`, phân trang và thời gian nằm tại [API conventions](conventions/api-conventions.md).

## Ca và sức chứa

| Mã ca | Giờ địa phương (`Asia/Ho_Chi_Minh`) | Slot 10 phút | Tối đa 20 phút | Tối đa 30 phút |
|---|---|---:|---:|---:|
| `MORNING` | 08:00–12:00 | 24 | 12 | 8 |
| `AFTERNOON` | 13:00–17:00 | 24 | 12 | 8 |
| `EVENING` | 17:00–20:00 | 18 | 9 | 6 |

Admin cấu hình `generalCapacity` và `specialistCapacity` cho từng phân công, không vượt mức của ca. Hai chỉ số là giới hạn riêng cho lượt 20 và 30 phút; ca hỗn hợp dùng **cùng một dãy slot**. Một lượt 20 phút chiếm hai slot liên tiếp, lượt 30 phút chiếm ba slot liên tiếp. Vì vậy tổng thời gian thực tế luôn bị giới hạn bởi 240 hoặc 180 phút. Một chỉ số đạt trần, hoặc không còn dãy slot phù hợp, khiến `full=true` đối với thời lượng đang hỏi.

Mỗi thời điểm trong response là `Instant` UTC. Client đổi sang giờ địa phương để hiển thị.

## Endpoint

Gốc: `/api/v1/doctor-schedules`.

| Method | Đường dẫn | Quyền | Nội dung |
|---|---|---|---|
| GET | `/shifts` | Người dùng đăng nhập trong `prod` | Ba ca cố định |
| POST | `/` | `ADMIN` | Tạo phân công và sinh slot |
| GET | `/` | `ADMIN` | Lọc theo `doctorId`, `clinicId`, `specialtyId`, `fromDate`, `toDate`, `status`; phân trang |
| GET | `/{publicId}` | `ADMIN` | Chi tiết phân công |
| PUT | `/{publicId}` | `ADMIN` | Sửa phân công khi chưa từng có lịch hẹn |
| PATCH | `/{publicId}/status` | `ADMIN` | Đổi `OPEN`, `CLOSED`, `CANCELLED` |
| GET | `/{publicId}/slots` | `ADMIN` | Slot và cờ `occupied` |
| PATCH | `/{publicId}/slots/{slotId}/status` | `ADMIN` | Chặn hoặc mở slot chưa có lịch hẹn |
| GET | `/availability` | Người dùng đăng nhập | Các khoảng còn đặt được |
| GET | `/my` | `DOCTOR` | Ca của bác sĩ đang đăng nhập |
| GET | `/my/{publicId}/patients` | `DOCTOR`, đúng ca được phân công | Bệnh nhân của ca theo thứ tự tiếp nhận |

`GET /` và `GET /my` dùng `page` bắt đầu từ 1, `size` tối đa 100, `sort` trong `workDate,startAt,status,createdAt`; mặc định theo ngày và giờ bắt đầu. `GET /availability` cũng phân trang theo **ca**, mỗi ca có mảng `windows` và cờ `full`. Bắt buộc `fromDate`, `toDate` (ISO `yyyy-MM-dd`); `durationMinutes` chỉ nhận `20` hoặc `30`, mặc định `20`. Bộ lọc bác sĩ, cơ sở, chuyên khoa là tùy chọn.

Ví dụ tạo ca:

```json
{
  "doctorId": 11,
  "specialtyId": 12,
  "clinicId": 13,
  "roomId": 14,
  "shiftId": 1,
  "workDate": "2026-11-03",
  "generalCapacity": 12,
  "specialistCapacity": 8
}
```

Thành công trả `201`, header `Location: /api/v1/doctor-schedules/{publicId}` và `{"data":{...}}`. Trước khi lưu, server xác thực bác sĩ còn làm việc và thuộc chuyên khoa, phòng thuộc cơ sở và cả hai còn mở. Hai phân công chưa hủy không được trùng thời gian của bác sĩ **hoặc** phòng. PostgreSQL giữ thêm exclusion constraint để tránh hai request đồng thời vượt qua bước kiểm tra ứng dụng.

`GET /availability` trừ các slot còn giữ chỗ (`appointment_slots.released_at IS NULL`), các slot `BLOCKED`, và giới hạn lượt theo loại. Đây là ảnh chụp tại thời điểm đọc; module đặt lịch phải kiểm tra và giữ slot trong một giao dịch riêng trước khi xác nhận. `CLOSED` không xuất hiện trong lịch khả dụng. `CANCELLED` chỉ được áp dụng khi ca chưa có lịch hẹn.

`GET /my/{publicId}/patients` chỉ trả ca thuộc bác sĩ đang đăng nhập. Mỗi dòng gồm mã đặt lịch, bệnh nhân, tên gói hoặc dịch vụ, trạng thái lịch hẹn, giờ hẹn và `receivedAt` nếu đã tiếp nhận. Bệnh nhân đã tiếp nhận đứng trước theo `receivedAt`, các trường hợp chưa tiếp nhận đứng sau theo giờ hẹn. Các trạng thái `HELD`, `CANCELLED`, `EXPIRED` không thuộc danh sách khám.

## Mã lỗi chính

| Mã | HTTP | Ý nghĩa |
|---|---:|---|
| `INVALID_SHIFT`, `INVALID_CAPACITY`, `INVALID_DATE_RANGE` | 400 | Ca, sức chứa hoặc khoảng ngày sai |
| `DOCTOR_NOT_FOUND`, `ROOM_NOT_FOUND`, `SCHEDULE_NOT_FOUND`, `SLOT_NOT_FOUND` | 404 | Không thấy đối tượng |
| `SCHEDULE_NOT_ASSIGNED` | 404 | Bác sĩ yêu cầu ca không thuộc mình |
| `DOCTOR_NOT_WORKING`, `DOCTOR_SPECIALTY_MISMATCH`, `FACILITY_NOT_OPEN`, `ROOM_CLINIC_MISMATCH` | 409 | Phân công không hợp lệ |
| `SCHEDULE_OVERLAP`, `SCHEDULE_HAS_APPOINTMENTS`, `SLOT_OCCUPIED` | 409 | Trùng ca hoặc đã có lịch hẹn |

`identity` chưa cung cấp JWT trong codebase hiện tại. Method security đã bật cho các route `ADMIN` và `DOCTOR`; cấu hình `prod` yêu cầu đăng nhập. Khi module `identity` được nối vào, principal cần có role tương ứng và liên kết tới user/doctor. Dữ liệu bệnh nhân chỉ được trả qua route `/my/{publicId}/patients` sau khi kiểm tra quyền sở hữu ca.
