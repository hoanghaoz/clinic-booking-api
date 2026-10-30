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
| GET | `/` | `ADMIN` | Lọc theo `doctorId`, `clinicId`, `specialtyId`, `fromDate`, `toDate`, `status`, `shiftId`; phân trang |
| GET | `/{publicId}` | `ADMIN` | Chi tiết phân công |
| PUT | `/{publicId}` | `ADMIN` | Sửa phân công khi chưa từng có lịch hẹn |
| PATCH | `/{publicId}/status` | `ADMIN` | Đổi `OPEN`, `CLOSED`, `CANCELLED` |
| PATCH | `/{publicId}/capacity` | `ADMIN` | Chỉnh sức chứa; không thể giảm dưới số lượt đang giữ chỗ |
| GET | `/{publicId}/slots` | `ADMIN` | Slot và cờ `occupied` |
| PATCH | `/{publicId}/slots/{slotId}/status` | `ADMIN` | Chặn hoặc mở slot chưa có lịch hẹn |
| GET | `/availability` | Người dùng đăng nhập | Các khoảng còn đặt được |
| GET | `/my` | `DOCTOR` | Ca của bác sĩ đang đăng nhập, có thể lọc theo `shiftId` |
| GET | `/my/{publicId}/patients` | `DOCTOR`, đúng ca được phân công | Bệnh nhân của ca theo thứ tự tiếp nhận |

`GET /` và `GET /my` dùng `page` bắt đầu từ 1, `size` tối đa 100, `sort` trong `workDate,startAt,status,createdAt`; mặc định theo ngày và giờ bắt đầu. `GET /availability` cũng phân trang theo **ca**, mỗi ca có mảng `windows` và cờ `full`. Bắt buộc `fromDate`, `toDate` (ISO `yyyy-MM-dd`); `durationMinutes` chỉ nhận `20` hoặc `30`, mặc định `20`. Bộ lọc bác sĩ, cơ sở, chuyên khoa và `shiftId` là tùy chọn.

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

`GET /availability` trừ các slot còn giữ chỗ (`appointment_slots.released_at IS NULL`), các slot `BLOCKED`, giờ đã qua và giới hạn lượt theo loại. Ca đã kết thúc, bác sĩ ngừng làm việc, phòng hoặc cơ sở đóng không xuất hiện. Các điều kiện nguồn lực được áp dụng trước khi phân trang, nên `totalElements` chỉ đếm ca hợp lệ. Lịch sử vẫn xuất hiện ở API admin và lịch cá nhân. Đây là ảnh chụp tại thời điểm đọc; module đặt lịch phải kiểm tra và giữ slot trong giao dịch trước khi xác nhận. `CLOSED` không xuất hiện trong lịch khả dụng. `CANCELLED` chỉ được áp dụng khi ca chưa có lịch hẹn. Mở lại ca phải xác thực lại bác sĩ, chuyên khoa và trạng thái cơ sở/phòng.

`GET /my/{publicId}/patients` chỉ trả ca thuộc bác sĩ đang đăng nhập. Mỗi dòng gồm mã đặt lịch, bệnh nhân, tên gói hoặc dịch vụ, trạng thái lịch hẹn, giờ hẹn và `receivedAt` nếu đã tiếp nhận. Bệnh nhân đã tiếp nhận đứng trước theo `receivedAt`, các trường hợp chưa tiếp nhận đứng sau theo giờ hẹn. Các trạng thái `HELD`, `CANCELLED`, `EXPIRED` không thuộc danh sách khám.

## Mã lỗi chính

| Mã | HTTP | Ý nghĩa |
|---|---:|---|
| `INVALID_SHIFT`, `INVALID_CAPACITY`, `INVALID_DATE_RANGE` | 400 | Ca, sức chứa hoặc khoảng ngày sai |
| `DOCTOR_NOT_FOUND`, `ROOM_NOT_FOUND`, `SCHEDULE_NOT_FOUND`, `SLOT_NOT_FOUND` | 404 | Không thấy đối tượng |
| `SCHEDULE_NOT_ASSIGNED` | 404 | Bác sĩ yêu cầu ca không thuộc mình |
| `DOCTOR_NOT_WORKING`, `DOCTOR_SPECIALTY_MISMATCH`, `FACILITY_NOT_OPEN`, `ROOM_CLINIC_MISMATCH` | 409 | Phân công không hợp lệ |
| `SCHEDULE_OVERLAP`, `SCHEDULE_HAS_APPOINTMENTS`, `SLOT_OCCUPIED` | 409 | Trùng ca hoặc đã có lịch hẹn |
| `CAPACITY_BELOW_BOOKED` | 409 | Sức chứa mới thấp hơn số lượt còn giữ chỗ |
| `INVALID_WINDOW` | 400 | Khoảng đặt chỗ không phải 20/30 phút ở tương lai |
| `SCHEDULE_NOT_OPEN`, `SLOT_UNAVAILABLE` | 409 | Ca không nhận lịch mới hoặc khung giờ không còn phù hợp |

## Đăng nhập và phân quyền

Ở dev, truyền `JWT_ENABLED=true` và `JWT_SECRET` ngẫu nhiên tối thiểu 32 byte vào môi trường
chạy Java. File `.env` chỉ được Docker Compose đọc, không tự cấp biến cho Gradle/Java.
Prod luôn bật JWT và không khởi động nếu thiếu secret hợp lệ. Không có secret mặc định.

`POST /api/v1/auth/login` nhận `{"username":"<username hoặc email>","password":"<mật khẩu>"}`.
Tài khoản phải có trạng thái `ACTIVE` và mật khẩu BCrypt trong `users.password_hashed`.
Response trả `data.accessToken`, `data.tokenType=Bearer`, `data.expiresIn=1800`; không cho cache.
Client gửi `Authorization: Bearer <accessToken>`. Token có subject là `users.public_id`, chữ ký HS256,
issuer/audience `clinic-booking-api` và thời hạn 30 phút. Role và trạng thái tài khoản được đọc lại
từ DB mỗi request, nên khóa tài khoản hoặc đổi role có hiệu lực với cả token đã cấp.

Sai mật khẩu/tài khoản bị khóa trả `401 INVALID_CREDENTIALS`; thiếu hoặc sai token trả
`401 AUTH_REQUIRED`; không đủ role trả `403 FORBIDDEN`. Bác sĩ cần được liên kết qua
`doctors.user_id`; dữ liệu bệnh nhân chỉ trả qua `/my/{publicId}/patients` sau khi kiểm tra ca
thuộc bác sĩ. Chưa triển khai đăng ký, quản trị tài khoản và refresh token; khi token hết hạn,
client đăng nhập lại bằng tài khoản đã được cấp.

## Tích hợp giữ chỗ cho booking

Các API sau là Service nội bộ, không phải endpoint công khai. Booking chịu trách nhiệm kiểm tra
bệnh nhân, gói/dịch vụ, giới hạn đặt trước, trùng lịch bệnh nhân, giá, lịch sử, QR và thông báo.

Trong **một transaction ghi**:

1. Gọi `ScheduleService.lockForBookingChange(scheduleId)` trước khi tạo/sửa lịch hẹn.
2. Tạo lịch hẹn `CONFIRMED` hoặc `HELD` có `hold_expires_at` còn hiệu lực.
3. Gọi `AppointmentReservationService.reserve(appointmentId)` trước khi commit.

Service kiểm tra lại nguồn lực, ca `OPEN`, giờ tương lai, sức chứa, dãy slot liên tục và trạng thái
lịch hẹn, rồi ghi các slot 10 phút. Khóa ghi trên ca được giữ tới cuối transaction; chỉnh phân công,
đổi sức chứa, chặn slot và hủy/mở ca cũng dùng cùng khóa. Unique index
`uk_appointment_slots_active_slot` bảo đảm một slot chỉ có một đặt chỗ chưa nhả, kể cả khi có writer
khác bỏ qua service. Lỗi giữa chừng phải rollback toàn bộ transaction.

Khi hủy hoặc hết hạn giữ chỗ, khóa ca trước, cập nhật trạng thái và lịch sử rồi gọi
`AppointmentReservationService.release(appointmentId)` trong cùng transaction. Đặt chỗ đã nhả
vẫn được giữ làm lịch sử và không giảm khả dụng. Khi đổi ca/giờ, dùng
`AppointmentReservationService.move(appointmentId, newScheduleId, start, end)`; service khóa ca cũ
và mới theo thứ tự ID, nhả ô cũ, kiểm tra/giữ ô mới và cập nhật giờ trong cùng giao dịch.
Nếu ca mới không còn chỗ, giờ và các ô cũ được khôi phục khi rollback. Caller ghi lịch sử nghiệp vụ
và cập nhật các snapshot/chính sách liên quan trong transaction đó.

Migration thêm unique index sẽ từ chối chạy nếu dữ liệu cũ đã có slot bị giữ trùng. Trước khi
nâng cấp DB có dữ liệu, kiểm tra và xử lý nghiệp vụ các dòng trùng; migration không tự xóa lịch hẹn:

```sql
select slot_id, count(*)
from appointment_slots
where released_at is null
group by slot_id
having count(*) > 1;
```
