# Quản lý cơ sở và phòng chức năng

Phạm vi Sprint 2 gồm RES-01 (cơ sở) và RES-02 (phòng thuộc cơ sở). Nhóm endpoint **Cơ sở và phòng** có trong Swagger ở profile `dev`. Để gọi API ở dev, bật `JWT_ENABLED=true`, cấu hình `JWT_SECRET` theo mục Bảo mật trong README và đăng nhập bằng tài khoản `ADMIN` đang hoạt động. Gửi access token dạng Bearer.

## Endpoint

Mọi đường dẫn dưới đây có tiền tố `/api/v1`. Dùng `publicId` dạng UUID cho cả hai tham số đường dẫn. Khi ghép với API phân ca, lấy `id` dạng số của cơ sở và phòng lần lượt làm `clinicId` và `roomId`.

| Method | Đường dẫn | Mục đích |
| --- | --- | --- |
| GET / POST | `/clinics` | Liệt kê / tạo cơ sở |
| GET / PUT | `/clinics/{clinicPublicId}` | Xem / sửa thông tin cơ sở |
| PATCH | `/clinics/{clinicPublicId}/status` | Mở lại / ngừng hoạt động cơ sở |
| GET / POST | `/clinics/{clinicPublicId}/rooms` | Liệt kê / tạo phòng thuộc cơ sở |
| GET / PUT | `/clinics/{clinicPublicId}/rooms/{roomPublicId}` | Xem / sửa mã và tên phòng |
| PATCH | `/clinics/{clinicPublicId}/rooms/{roomPublicId}/status` | Mở lại / ngừng hoạt động phòng |

POST trả 201 và header `Location`. Kết quả trả theo [quy ước API](conventions/api-conventions.md). Danh sách dùng `page` từ 1, `size` mặc định 20 và tối đa 100, `keyword` tìm theo mã/tên, `status` nhận `OPENING` hoặc `CLOSING`. Sort mặc định theo `name`. Các trường cho phép gồm `code`, `name`, `status`, `createdAt`.

```http
GET /api/v1/clinics?keyword=an%20tam&status=OPENING&page=1&size=10&sort=-createdAt,name
```

POST và PUT cơ sở nhận `code`, `name`, `address`, `phoneNumber`, cùng `email` và `description` tùy chọn. Gửi `null` cho trường tùy chọn để bỏ giá trị. POST và PUT phòng nhận `code` và `name`. Mã cơ sở duy nhất toàn hệ thống, mã phòng duy nhất trong cơ sở, theo ràng buộc của cơ sở dữ liệu.

## Trạng thái và lịch phân ca

- Cơ sở mới có trạng thái `OPENING`. Phòng mới lấy trạng thái ban đầu của cơ sở.
- Đổi trạng thái qua PATCH với `{"status":"CLOSING"}` hoặc `{"status":"OPENING"}`. Tạm ngừng cơ sở giữ nguyên trạng thái riêng của từng phòng. API phân ca chỉ cho dùng phòng khi cả phòng và cơ sở đều đang hoạt động.
- Cơ sở hoặc phòng có ca đang diễn ra hoặc sắp tới (giờ kết thúc còn ở tương lai và ca chưa hủy) trả `409 FACILITY_HAS_SCHEDULES` khi yêu cầu ngừng hoạt động. Xử lý ca trước khi đổi trạng thái.
- Mở lại phòng dưới cơ sở đang ngừng hoạt động trả `409 CLINIC_CLOSED`.
- Mã trùng trả `409 CLINIC_CODE_EXISTS` hoặc `409 ROOM_CODE_EXISTS`. Đường dẫn sai cơ sở hoặc sai phòng trả `404 CLINIC_NOT_FOUND` hoặc `404 FACILITY_ROOM_NOT_FOUND`.
