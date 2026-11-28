# Quản lý lịch hẹn · BOOK-05 đến BOOK-08

Bật JWT theo README. API bệnh nhân cần tài khoản `PATIENT` đã liên kết hồ sơ. API tại quầy cần tài khoản `MEDICAL_STAFF`, bản ghi `staffs` đang `WORKING` và cơ sở được gán trong `clinic_staffs`.

## Endpoint

Tiền tố `/api/v1/appointments`. Các tham số `{id}` là UUID công khai của lịch hẹn.

| Method | Endpoint | Công dụng |
| --- | --- | --- |
| GET | `/me?group=UPCOMING` | Lịch cá nhân, các nhóm `UPCOMING`, `COMPLETED`, `CANCELLED`, `OTHER` |
| GET / PATCH | `/me/{id}` | Chi tiết kèm QR, hủy hoặc đổi giờ lịch của mình |
| GET | `/me/{id}/availability?clinicId=1&date=2026-12-01` | Tìm giờ đổi theo thời lượng và chuyên khoa của ca hiện tại |
| GET | `/manage/clinics` | Các cơ sở của nhân viên |
| GET | `/manage?clinicId=1&term=...` | Tra cứu chính xác theo mã lịch, điện thoại hoặc giấy tờ |
| GET | `/manage?clinicId=1&daily=true&date=2026-12-01` | Danh sách dự kiến theo ngày tại Việt Nam, bỏ lịch hủy và lịch giữ chỗ |
| GET / PATCH | `/manage/{id}` | Chi tiết, xác nhận lịch giữ chỗ, chuyển ca hoặc hủy |
| GET | `/manage/services` | Dịch vụ lẻ dùng khi đặt hộ |
| GET | `/manage/availability?clinicId=1&date=2026-12-01&durationMinutes=20` | Giờ còn trống khi đặt hộ. Khi chuyển ca, gửi `appointmentId` để lấy thời lượng và chuyên khoa của lịch |
| POST | `/manage` | Đặt hộ với `{patientPublicId, booking}`, trong đó `booking` dùng [request BOOK-02](booking-api.md#xác-nhận-và-nhận-phiếu) |

Danh sách dùng `page` và `size` cùng wrapper `{data, meta}`. Khi `daily=true` và bỏ `date`, ngày mặc định là hôm nay theo giờ Việt Nam. `depositPaid` là tổng tiền tạm ứng từ các giao dịch `DEPOSIT/SUCCEEDED`, còn `depositRequired` là số cần thu. Nhân viên chỉ đọc hoặc thay đổi lịch tại cơ sở được phân công, kể cả cơ sở đích khi chuyển ca.

## Hủy, đổi và xác nhận

- Hủy gửi `{"action":"CANCEL","reason":"..."}`. Từ đủ 24 giờ trước hẹn ghi `CANCELLED` và tỷ lệ hoàn tạm ứng theo chính sách đã lưu. Sát giờ hơn ghi `LATE_CANCELLED` kèm tỷ lệ hoàn 0%. Hoàn tiền thuộc luồng thanh toán.
- Đổi gửi `{"action":"RESCHEDULE","schedulePublicId":"<uuid>","startAt":"2026-12-01T01:00:00Z"}`. Bệnh nhân tự đổi trước hoặc đúng mốc 24 giờ. Sau mốc này, nhân viên có thể chuyển ca trước giờ hẹn. Ca mới giữ nguyên chuyên khoa, thời lượng và giá đã chốt. Đổi sang đúng ca và giờ hiện tại trả lịch hiện tại.
- Nhân viên xác nhận lịch `HELD` còn hạn bằng `{"action":"CONFIRM"}`. Lịch `CONFIRMED` còn ở tương lai trả lại dữ liệu hiện tại khi xác nhận lặp.
- Lịch đã bắt đầu, đã tiếp nhận, đã hoàn thành, đã hủy hoặc giữ chỗ đã hết hạn trả lỗi 409 khi thực hiện thao tác. Lịch đã qua giờ mà chưa tiếp nhận hiển thị `OVERDUE`.
- Đổi lịch dùng chung kiểm tra xung đột của luồng đặt lịch và thao tác giữ/nhả ô trong một giao dịch. Backend lưu trạng thái, thời gian cũ/mới, người thao tác và lý do vào lịch sử.
