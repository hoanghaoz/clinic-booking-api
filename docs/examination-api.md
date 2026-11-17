# API khám bệnh của bác sĩ

Module `examination` triển khai REC-05, EXAM-01, EXAM-02, EXAM-03. Quy ước chung về `ApiResult`, `ProblemDetail`, phân trang và thời gian nằm tại [API conventions](conventions/api-conventions.md).

## Ranh giới

- Lễ tân tạo lượt khám (`medical_visits`, có `received_by_staff_id`) khi tiếp nhận bệnh nhân. Module này **không tạo** lượt khám, chỉ đọc và cập nhật phần bác sĩ ghi.
- Danh sách ở đây khác `GET /doctor-schedules/my/{publicId}/patients` của module `schedule`: danh sách đó theo **lịch hẹn** (có cả người chưa đến), danh sách này theo **lượt khám đã tiếp nhận**.
- Tiền sử bệnh lý lấy qua `PatientMedicalHistoryService` (PAT-03) của module `patient`.

## Bác sĩ phụ trách một lượt khám

Là `primary_doctor_id` của lượt khám; nếu chưa có thì là bác sĩ của ca mà lịch hẹn được đặt vào. Lượt khám của bác sĩ khác trả `403 VISIT_NOT_ASSIGNED`. Bác sĩ bấm bắt đầu hoặc lưu lần đầu thì trở thành bác sĩ khám chính.

## Endpoint

Gốc: `/api/v1/medical-visits`. Tất cả cần vai trò `DOCTOR`.

| Method | Đường dẫn | Nội dung |
|---|---|---|
| GET | `/my-queue` | Bệnh nhân đã tiếp nhận của bác sĩ theo thứ tự tiếp nhận |
| GET | `/{publicId}` | Mở hồ sơ lượt khám: bệnh nhân, lịch hẹn, ca, chỉ định, phần đã ghi |
| POST | `/{publicId}/start` | `WAITING` → `IN_PROGRESS`; gọi lại không đổi |
| PUT | `/{publicId}/examination` | Ghi triệu chứng, chẩn đoán ban đầu, tư vấn, sinh hiệu |
| GET | `/{publicId}/patient-history` | Tiền sử, lịch sử khám và kết quả cận lâm sàng cũ |

### `GET /my-queue`

Query tùy chọn: `schedulePublicId`, `date` (`yyyy-MM-dd`), `status`. Không truyền `schedulePublicId` và `date` thì lấy các ca **hôm nay** (giờ `Asia/Ho_Chi_Minh`). Chỉ gồm lượt khám đã có phiếu tiếp nhận, chưa hủy, và lịch hẹn chưa hủy.

Sắp xếp theo giờ bắt đầu ca, rồi `receivedAt`, rồi id lượt khám, nên thứ tự không đổi giữa các lần tải. `queueNumber` là số thứ tự trong ca, tính trước khi lọc `status` nên lọc không làm đổi số. Không có ai thì `data` là mảng rỗng.

### `PUT /{publicId}/examination`

Thay toàn bộ phần bác sĩ ghi; trường bỏ trống bị xóa.

```json
{
  "version": 0,
  "symptoms": "Ho, sốt 2 ngày",
  "clinicalNotes": null,
  "initialDiagnosis": "Viêm họng cấp",
  "advice": "Uống nhiều nước, nghỉ ngơi",
  "heightCm": 170,
  "weightKg": 65,
  "temperatureC": 38.5,
  "heartRate": 90,
  "systolicBp": 120,
  "diastolicBp": 80
}
```

`version` bắt buộc, lấy từ lần GET gần nhất. Nếu lượt khám đã được lưu ở nơi khác (tab khác, thiết bị khác) thì trả `409 VISIT_VERSION_CONFLICT`; client tải lại rồi lưu lại, dữ liệu đã lưu không bị ghi đè. Khoảng hợp lệ: chiều cao 30–250 cm, cân nặng 1–500 kg, thân nhiệt 30–45 °C, nhịp tim 20–250, huyết áp tâm thu 40–300, tâm trương 20–200 và phải nhỏ hơn tâm thu.

### `GET /{publicId}/patient-history`

```json
{
  "data": {
    "medicalHistory": { "personal": [], "family": [], "allergies": [] },
    "previousVisits": [
      {
        "publicId": "...", "code": "...", "status": "COMPLETED", "receivedAt": "...",
        "symptoms": "...", "initialDiagnosis": "...", "finalDiagnosis": "...", "conclusion": "...",
        "results": [ { "itemName": "Công thức máu", "resultStatus": "VERIFIED", "resultText": "..." } ]
      }
    ]
  }
}
```

Chỉ bác sĩ phụ trách lượt khám mới xem được. `previousVisits` là tối đa 20 lượt cũ hơn của cùng bệnh nhân (không tính lượt hiện tại, không tính lượt đã hủy), mới nhất trước. `results` chỉ gồm kết quả đã duyệt (`VERIFIED`, `RELEASED`), kết quả nháp không hiện. Bệnh nhân chưa có dữ liệu thì các mảng rỗng, không lỗi.

## Vòng đời trạng thái lượt khám

`WAITING` → `IN_PROGRESS` → `AWAITING_RESULTS` → `COMPLETED`; `CANCELLED` từ lúc tiếp nhận. Module này chuyển `WAITING` → `IN_PROGRESS`. `COMPLETED` và `CANCELLED` là đã đóng hồ sơ: `start` và `PUT` trả `409 VISIT_CLOSED`.

## Mã lỗi

| Mã | HTTP | Khi nào |
|---|---:|---|
| `VISIT_NOT_FOUND` | 404 | Không có lượt khám với `publicId` |
| `VISIT_NOT_ASSIGNED` | 403 | Lượt khám của bác sĩ khác |
| `VISIT_DOCTOR_PROFILE_NOT_FOUND` | 403 | Tài khoản `DOCTOR` chưa gắn với hồ sơ bác sĩ |
| `VISIT_CLOSED` | 409 | Lượt khám đã `COMPLETED` hoặc `CANCELLED` |
| `VISIT_VERSION_CONFLICT` | 409 | `version` cũ hơn bản đã lưu |
| `INVALID_VITAL_SIGNS` | 400 | Huyết áp tâm trương không nhỏ hơn tâm thu |

Thiếu `version` hoặc giá trị ngoài khoảng trả `400` với danh sách trường lỗi. Chưa đăng nhập `401`; sai vai trò `403 FORBIDDEN`.

## Ghi chú kỹ thuật

- Bảng `medical_visits` có cột `version` (migration `V20261116_1000`); entity dùng `@Version` để Hibernate chặn ghi đè đồng thời.
- Các truy vấn nối bảng của nhiều module (`ExaminationReadRepository`) dùng SQL thuần, giống `AppointmentSchedulingService`, vì các module không chia sẻ quan hệ JPA.
