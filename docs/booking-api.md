# API đặt lịch khám

Backend triển khai BOOK-01 đến BOOK-04. Gốc API là `/api/v1/appointments`.
Các endpoint yêu cầu JWT của tài khoản `PATIENT`. Tìm giờ, tạo lịch và đọc phiếu
dùng hồ sơ đã liên kết với tài khoản. Khi chạy dev, bật `JWT_ENABLED=true` và
cấu hình `JWT_SECRET` theo [hướng dẫn JWT](schedule-api.md#đăng-nhập-và-phân-quyền).

| Method | Đường dẫn | Công dụng |
|---|---|---|
| GET | `/clinics` | Liệt kê cơ sở đang mở, trả ID dùng để tìm giờ |
| GET | `/services` | Liệt kê dịch vụ lẻ có thời lượng đặt lịch 20 hoặc 30 phút |
| GET | `/availability` | Tìm khung giờ theo cơ sở, bác sĩ, chuyên khoa, gói hoặc dịch vụ |
| POST | `/` | Xác nhận đặt lịch và trả phiếu có QR, HTTP 201 |
| GET | `/{publicId}/ticket` | Đọc lại phiếu của chính bệnh nhân |

## Tìm giờ và chọn nội dung khám

Bệnh nhân chọn cơ sở từ `/clinics`. Có thể lấy chuyên khoa đang hoạt động từ
`GET /api/v1/specialties?active=true` và bác sĩ đang làm việc từ
`GET /api/v1/doctors?status=WORKING`. Các bộ lọc được kết hợp khi tìm giờ.

`GET /availability` nhận `clinicId`, `fromDate` và `toDate` (ngày theo giờ
Việt Nam). Có thể lọc thêm bằng `doctorId`, `specialtyId`, `packagePublicId`
hoặc `servicePublicId`. Khi chọn gói/dịch vụ, backend lấy thời lượng từ danh mục.
Khi chỉ tìm theo bác sĩ/chuyên khoa, `durationMinutes` nhận 20 hoặc 30, mặc định
20. Tìm giờ theo bác sĩ/chuyên khoa vẫn cần chọn một dịch vụ lẻ hoặc gói khi xác nhận.

Kết quả phân trang theo ca. Mỗi ca có `schedule`, `windows`, `full` và
thông tin giá/phiên bản khi đã chọn gói/dịch vụ. Bệnh nhân cá nhân (`PERSONAL`)
chỉ thấy ngày đến hết ngày thứ 30 tính từ ngày hiện tại.

Gói nhận đặt lịch khi có phiên bản đang hiệu lực, trạng thái `OPENING` hoặc
`DISCOUNT`, thời lượng 20/30 phút và ít nhất một chuyên khoa đã cấu hình.
Admin dùng `PUT /api/v1/medical-packages/{packagePublicId}/specialties` với
`{"specialtyIds":[12,13]}` để cấu hình. Các gói đã tồn tại cần được gán chuyên
khoa trước khi mở đặt lịch. Gọi GET cùng đường dẫn để xem cấu hình.

## Xác nhận và nhận phiếu

Gửi `schedulePublicId`, `clinicId`, `doctorId` và `startAt` từ khung giờ đã
chọn. Gửi một trong hai lựa chọn sau.

- `packagePublicId` và `packageVersion` trả từ kết quả tìm giờ.
- `servicePublicId` từ danh sách dịch vụ lẻ.

```json
{
  "schedulePublicId": "9580cbde-8501-41b8-ab41-5b2f28a150ef",
  "clinicId": 13,
  "doctorId": 11,
  "startAt": "2026-11-20T01:00:00Z",
  "packagePublicId": "c102cfcb-c5bf-4f5e-b135-f8ba0fdabccf",
  "packageVersion": "v1.0"
}
```

Response `201` có `Location` trỏ đến phiếu, chứa mã đặt lịch, ảnh QR PNG mã
hóa Base64, giờ khám, bác sĩ, cơ sở, giá đã chốt, hướng dẫn chuẩn bị và số tạm
ứng. Khi chưa có chính sách thu tạm ứng, backend xác nhận ngay và ghi số tiền
tạm ứng bằng 0. QR mã hóa đúng trường `code`. Có thể hiển thị ảnh bằng
`data:image/png;base64,` nối với `qrPngBase64`.

Backend kiểm tra hồ sơ, quyền đặt, giới hạn ngày, tuổi vào ngày khám, giới tính,
phiên bản gói, trùng giờ và hai cơ sở trong cùng ngày/ca. Khi xác nhận, giao
dịch khóa hồ sơ bệnh nhân trước khi khóa ca, chốt giá và phiên bản, rồi giữ 2
hoặc 3 ô 10 phút liên tiếp. Nếu giữ chỗ thất bại, giao dịch rollback cả lịch
hẹn và lịch sử. Bệnh nhân thuộc hợp đồng doanh nghiệp xếp lịch qua luồng doanh
nghiệp. Kiểm tra thẻ hết hạn sẽ theo quy tắc của hồ sơ bệnh nhân khi nhóm chốt.

| Mã lỗi | HTTP | Xử lý |
|---|---:|---|
| `PATIENT_PROFILE_REQUIRED` | 409 | Hoàn tất liên kết hồ sơ |
| `ONLINE_BOOKING_RESTRICTED` | 403 | Hiển thị lý do hoặc thời điểm hết khóa |
| `BOOKING_DATE_LIMIT`, `BOOKING_START_IN_PAST` | 400 | Chọn ngày giờ hợp lệ |
| `INVALID_BOOKING_PRODUCT` | 400 | Chọn gói kèm phiên bản hoặc dịch vụ lẻ |
| `MEDICAL_PACKAGE_VERSION_CHANGED` | 409 | Tải lại thông tin giá và giờ trống |
| `MEDICAL_PACKAGE_NOT_BOOKABLE`, `MEDICAL_SERVICE_NOT_BOOKABLE` | 409 | Chọn nội dung khám khác |
| `BOOKING_PRODUCT_INELIGIBLE`, `BOOKING_SPECIALTY_MISMATCH` | 409 | Chọn gói hoặc ca phù hợp |
| `APPOINTMENT_OVERLAP`, `APPOINTMENT_OTHER_CLINIC_SAME_SHIFT` | 409 | Chọn ca không xung đột |
| `BOOKING_SCHEDULE_CHANGED`, `SLOT_UNAVAILABLE`, `SLOT_OCCUPIED` | 409 | Tải lại giờ trống |

## Tích hợp nội bộ

- Quản lý lịch hẹn gọi `AppointmentBookingService.bookAtDesk(patientId, staffId, request)`
  sau khi kiểm tra vai trò và phạm vi cơ sở của nhân viên. Khi đổi/hủy, dùng
  `AppointmentReservationService.move/release` theo [hợp đồng giữ chỗ](schedule-api.md#tích-hợp-giữ-chỗ-cho-booking).
  Luồng ghi kiểm tra xung đột bệnh nhân cần lấy khóa qua
  `PatientBookingService.lockForPatient` trước khi khóa ca.
- Tiếp nhận gọi `AppointmentBookingService.findByQr(payload, clinicId)` với cơ
  sở nhân viên được phép truy cập. Kết quả dùng để tra cứu. Bước tiếp nhận vẫn
  kiểm tra ngày, trạng thái lịch hẹn và quyền của nhân viên.
