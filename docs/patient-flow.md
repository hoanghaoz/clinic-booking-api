# Luồng bệnh nhân

Mô tả luồng bệnh nhân từ lúc đăng ký đến sau khi khám, theo `docs/de-tai.md` và các entity/enum
trong code (`appointment`, `examination`, `payment`, `violation`, `notification`, `company`).
Đây là thiết kế dự kiến, không phải mô tả hành vi đã chạy: xem mục "Trạng thái triển khai" ở cuối.

## 1. Tổng quan luồng

```mermaid
flowchart TD
    A([Bệnh nhân]) --> B[Đăng ký tài khoản và hồ sơ<br/>họ tên, ngày sinh, CCCD, SĐT, email]
    B --> C[Cấp mã bệnh nhân và thẻ điện tử<br/>hiệu lực 1 năm]
    C --> D[Tra cứu gói khám, hạng mục, bác sĩ, giá]
    D --> E[Chọn cơ sở, chuyên khoa hoặc gói hoặc bác sĩ]
    E --> F{Còn giờ trống?}
    F -- Có --> G[Chọn ngày giờ và xác nhận]
    F -- Hết chỗ --> W[Đăng ký danh sách chờ]
    W -. có người hủy hoặc đổi lịch .-> N1[Thông báo ưu tiên cho người đăng ký trước]
    N1 --> G
    G --> H{Kiểm tra ràng buộc}
    H -- "Trùng giờ, 2 cơ sở cùng ca,<br/>quá 30 ngày, đang bị khóa đặt lịch" --> X[Từ chối và báo lý do]
    H -- Hợp lệ --> I[Giữ chỗ HELD hoặc CONFIRMED<br/>sinh Phiếu Đặt Lịch có QR]
    I --> J[Gửi email và thông báo in-app<br/>kèm dặn chuẩn bị y tế]
    J --> K{Bệnh nhân làm gì?}
    K -- Hủy hoặc đổi lịch --> L{Trước giờ khám 24 giờ?}
    L -- Có --> M[Hoàn 100 phần trăm tạm ứng<br/>hoặc dời lịch miễn phí]
    L -- Không --> V1[Ghi vi phạm LATE_CANCELLED]
    K -- Không đến --> V2[NO_SHOW và ghi vi phạm]
    V2 --> V3{Quá 3 lần trong 6 tháng?}
    V3 -- Có --> V4[Khóa đặt lịch online 3 tháng]
    K -- Đến khám --> R[Lễ tân quét QR hoặc tra SĐT/CCCD<br/>kiểm tra thẻ còn hạn]
    R --> S[CHECKED_IN, tạo Phiếu Tiếp Nhận<br/>phân luồng vào phòng khám]
    S --> T[Bác sĩ khám, ghi triệu chứng, chẩn đoán]
    T --> U{Cần chỉ định thêm?}
    U -- Có --> U1[Chỉ định dịch vụ phát sinh<br/>đẩy sang thanh toán và phòng chức năng]
    U1 --> U2[Phòng chức năng trả kết quả]
    U -- Không --> U2
    U2 --> Y[Phiếu Kết Quả, đơn thuốc điện tử, hẹn tái khám]
    Y --> Z[Thanh toán chi phí phát sinh, xuất hóa đơn]
    Z --> AA([Bệnh nhân xem kết quả, đơn thuốc, tiền sử trực tuyến])
```

## 2. Đặt lịch (trình tự)

```mermaid
sequenceDiagram
    actor BN as Bệnh nhân
    participant API as Backend
    participant SCH as Lịch làm việc
    participant APP as Lịch hẹn
    participant NOTI as Thông báo

    BN->>API: Tìm giờ trống (cơ sở, chuyên khoa, ngày, thời lượng 20 hoặc 30 phút)
    API->>SCH: Lấy các ca OPEN và slot còn trống
    SCH-->>API: Các khoảng còn đặt được
    API-->>BN: Danh sách giờ trống
    BN->>API: Chọn giờ và xác nhận
    API->>APP: Kiểm tra trùng giờ, 2 cơ sở cùng ca, quá 30 ngày, khóa đặt lịch
    APP->>SCH: Khóa slot và giữ chỗ (reserve)
    alt Slot đã có người giữ
        SCH-->>API: SLOT_OCCUPIED (409)
        API-->>BN: Gợi ý danh sách chờ
    else Giữ chỗ thành công
        APP-->>API: Lịch hẹn HELD hoặc CONFIRMED
        API->>NOTI: APPOINTMENT_CONFIRMED (email và in-app)
        API-->>BN: Phiếu Đặt Lịch và mã QR
    end
```

## 3. Trạng thái lịch hẹn

Theo `AppointmentStatus` và `AppointmentAction`. Các mũi tên là luồng dự kiến theo đề tài.

```mermaid
stateDiagram-v2
    [*] --> HELD: giữ chỗ có hạn (holdExpiresAt)
    [*] --> CONFIRMED: nhân viên hoặc công ty đặt
    HELD --> CONFIRMED: thanh toán hoặc xác nhận
    HELD --> EXPIRED: hết hạn giữ chỗ
    CONFIRMED --> CONFIRMED: đổi lịch (RESCHEDULED)
    CONFIRMED --> CANCELLED: hủy (trước 24 giờ hoàn tiền)
    CONFIRMED --> CANCELLED: hủy sát giờ (LATE_CANCELLED, ghi vi phạm)
    CONFIRMED --> NO_SHOW: không đến
    CONFIRMED --> CHECKED_IN: lễ tân tiếp nhận
    CHECKED_IN --> COMPLETED: bác sĩ hoàn tất hồ sơ khám
    CANCELLED --> [*]
    NO_SHOW --> [*]
    EXPIRED --> [*]
    COMPLETED --> [*]
```

## 4. Danh sách chờ

Theo `WaitlistStatus`.

```mermaid
stateDiagram-v2
    [*] --> WAITING: khung giờ đã kín
    WAITING --> OFFERED: có người hủy hoặc đổi, thông báo ưu tiên
    OFFERED --> BOOKED: bệnh nhân đặt trong thời hạn
    OFFERED --> EXPIRED: quá thời hạn, chuyển cho người kế tiếp
    WAITING --> CANCELLED: bệnh nhân rút
    OFFERED --> CANCELLED: bệnh nhân từ chối
    BOOKED --> [*]
    EXPIRED --> [*]
    CANCELLED --> [*]
```

## 5. Phiếu khám và chỉ định

Theo `MedicalVisitStatus` và `MedicalOrderStatus`.

```mermaid
stateDiagram-v2
    state "Phiếu khám (MedicalVisit)" as V {
        [*] --> WAITING
        WAITING --> IN_PROGRESS: bác sĩ mở hồ sơ
        IN_PROGRESS --> AWAITING_RESULTS: đã chỉ định cận lâm sàng
        AWAITING_RESULTS --> IN_PROGRESS: có đủ kết quả
        IN_PROGRESS --> COMPLETED: lập phiếu kết quả, đóng hồ sơ
        WAITING --> CANCELLED
    }
    state "Chỉ định (MedicalOrder)" as O {
        [*] --> ORDERED
        ORDERED --> WAITING: đã đẩy sang phòng chức năng
        WAITING --> IN_PROGRESS
        IN_PROGRESS --> COMPLETED: có kết quả
        IN_PROGRESS --> DEFERRED: không tuân thủ chuẩn bị, hẹn bù
        ORDERED --> CANCELLED
    }
```

## 6. Bệnh nhân thuộc hợp đồng doanh nghiệp

Bệnh nhân loại `COMPANY` không tự đặt lịch. Doanh nghiệp gửi danh sách chốt từ HR, hệ thống tạo lịch
hẹn với nguồn `COMPANY` cho từng thành viên hợp đồng.

```mermaid
flowchart LR
    HR([HR doanh nghiệp]) --> U[Gửi danh sách khám đã chốt]
    U --> C[Đối chiếu hợp đồng và gói khám]
    C --> L[Tạo lịch hẹn nguồn COMPANY cho từng thành viên]
    L --> NT[Thông báo COMPANY_IMPORT_COMPLETED]
    L --> F[Các bước khám giống luồng cá nhân]
```

## 7. Trạng thái triển khai

| Bước | Có API | Ghi chú |
|---|---|---|
| Cấu hình cơ sở, phòng, chuyên khoa, bác sĩ, gói khám, lịch làm việc | Có | Phía Admin, đã test |
| Tìm giờ trống (`GET /api/v1/doctor-schedules/availability`) | Có | Cần đăng nhập |
| Hàng đợi bác sĩ (`GET /api/v1/doctor-schedules/my/{id}/patients`) | Có | Dữ liệu rỗng vì chưa có lịch hẹn |
| Đăng ký tài khoản và hồ sơ bệnh nhân | Chưa | Entity và migration đã có |
| Đặt, hủy, đổi lịch hẹn; danh sách chờ | Chưa | Có `AppointmentReservationService` (nội bộ) |
| Tiếp nhận, khám, chỉ định, kết quả | Chưa | Entity `examination` đã có |
| Thanh toán, hoàn tiền, hóa đơn | Chưa | Entity `payment` đã có |
| Vi phạm, khóa đặt lịch, thông báo | Chưa | Entity đã có |

Câu hỏi còn mở từ đề tài (mục 6): ràng buộc sức chứa cho ca ngoài giờ 3 giờ chưa được chốt.
