# clinic-booking-api

Backend cho Hệ thống quản lý đặt lịch khám bệnh & dịch vụ y tế (chuỗi phòng khám nhiều chi
nhánh) — xem mô tả nghiệp vụ đầy đủ ở [`docs/de-tai.md`](docs/de-tai.md).

- **Stack:** Java 25 + Spring Boot 4.1.1 + Gradle (Kotlin DSL, dùng qua Gradle Wrapper) +
  PostgreSQL + Flyway.
- **FE:** ReactJS, nằm ở **repo riêng** (không phải repo này).
- **Kiến trúc:** Modular Monolith, package-by-feature phẳng (xem mục "Kiến trúc & quy tắc
  module" bên dưới).
- Lý do các lựa chọn kỹ thuật và việc để dành làm sau (Modulith, JWT...):
  [`docs/setup/DECISIONS.md`](docs/setup/DECISIONS.md).
- Người mới học Java/Spring (từ nền C#/NestJS) nên đọc
  [`docs/JAVA_FOR_NESTJS_DEVS.md`](docs/JAVA_FOR_NESTJS_DEVS.md) trước.

## Mục lục

- [Chạy local từng bước](#chạy-local-từng-bước)
- [Danh sách lệnh hay dùng](#danh-sách-lệnh-hay-dùng)
- [Kiến trúc & quy tắc module](#kiến-trúc--quy-tắc-module)
- [Bảng module](#bảng-module)
- [Cách thêm 1 module mới](#cách-thêm-1-module-mới)
- [Commit & branch convention](#commit--branch-convention)
- [Git hooks (Lefthook)](#git-hooks-lefthook)
- [Bảo mật](#bảo-mật)
- [FE generate TypeScript type từ OpenAPI](#fe-generate-typescript-type-từ-openapi)
- [Cài đặt IntelliJ IDEA](#cài-đặt-intellij-idea)
- [Bật branch protection cho main (làm tay trên GitHub)](#bật-branch-protection-cho-main-làm-tay-trên-github)
- [Quyết định còn mở](#quyết-định-còn-mở)

## Chạy local từng bước

1. **Cài công cụ cần có trên máy** (không có sẵn trong repo, phải tự cài):
   - JDK **25** (LTS) — kiểm tra bằng `java -version`.
   - Docker + Docker Compose — kiểm tra bằng `docker --version` và `docker compose version`.
   - (Khuyến nghị) Node.js/npm — chỉ dùng để cài Git hooks (Lefthook), KHÔNG dùng để chạy
     backend. Kiểm tra bằng `npm -v`. Không có cũng không sao, xem
     [Git hooks (Lefthook)](#git-hooks-lefthook).
   - KHÔNG cần cài Gradle — dùng `./gradlew` (Gradle Wrapper) đi kèm repo.

2. **Tạo file `.env`** (không bắt buộc nếu dùng giá trị mặc định):
   ```bash
   cp .env.example .env
   ```

3. **Bật Postgres + Mailpit:**
   ```bash
   docker compose up -d
   ```
   - Postgres: `localhost:5432` (user/pass mặc định: `clinic`/`clinic`, DB `clinic_booking`).
   - Mailpit Web UI (xem email "đã gửi"): http://localhost:8025

4. **Build & chạy app:**
   ```bash
   ./gradlew bootRun
   ```
   Lần đầu chạy sẽ tự tải Gradle distribution + toàn bộ dependency (có thể mất vài phút tuỳ
   mạng) và tự cài Git hooks (xem [Git hooks (Lefthook)](#git-hooks-lefthook)).

5. **Kiểm tra:**
   - Health check: http://localhost:8080/actuator/health → `{"status":"UP"}`
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

## Danh sách lệnh hay dùng

| Lệnh | Mục đích |
|---|---|
| `./gradlew bootRun` | Chạy app (profile mặc định: `dev`) |
| `./gradlew build` | Build đầy đủ: compile + unit test + package jar + cài Git hooks |
| `./gradlew test` | Chỉ chạy unit test (nhanh, KHÔNG cần Docker) |
| `./gradlew integrationTest` | Chạy integration test (Testcontainers, CẦN Docker đang chạy) |
| `./gradlew spotlessApply` | Tự động format code theo google-java-format |
| `./gradlew spotlessCheck` | Kiểm tra format mà không tự sửa (dùng trong CI) |
| `./gradlew checkstyleMain checkstyleTest` | Xem cảnh báo style (không fail build) |
| `./gradlew jacocoTestReport` | Sinh report coverage tại `build/reports/jacoco/test/html` |
| `./gradlew installGitHooks` | Cài lại Git hooks bằng tay nếu cần |
| `docker compose up -d` | Bật Postgres + Mailpit |
| `docker compose down` | Tắt Postgres + Mailpit (giữ lại data, xem `-v` để xoá luôn data) |

## Kiến trúc & quy tắc module

Modular Monolith — mỗi nghiệp vụ lớn là 1 package Java top-level dưới `com.se100.clinic`,
bên trong PHẲNG (không chia sub-package theo layer kiểu `controller/`, `service/`,
`repository/`):

```
com.se100.clinic
├── booking/
│   ├── BookingController.java    ← package-private
│   ├── BookingService.java       ← PUBLIC (API duy nhất cho module khác gọi)
│   ├── BookingRepository.java    ← package-private
│   ├── Appointment.java          ← entity, package-private
│   ├── BookingDtos.java          ← record DTO, public nếu module khác cần
│   └── AppointmentCancelled.java ← domain event, public
├── doctor/    ← MODULE MẪU, xem chi tiết bên dưới
├── shared/    ← exception, audit entity, Role — module khác được import trực tiếp
└── config/    ← cấu hình framework (Security/CORS/OpenAPI/Scheduling), không phải "module"
```

### 3 quy tắc bắt buộc

1. **Chỉ `Service`, DTO (`record`), và domain event được `public`.** `Controller`,
   `Repository`, `Entity` PHẢI để package-private (không ghi từ khoá truy cập nào trước
   `class`/`interface`). Đây không chỉ là quy ước — Java compiler THỰC SỰ chặn module khác
   import class package-private, khác package sẽ bị lỗi biên dịch.
2. **Module khác chỉ được gọi qua `Service` public**, hoặc lắng nghe domain event qua
   `ApplicationEventPublisher` + `@TransactionalEventListener`. KHÔNG bao giờ
   `@Autowired`/inject thẳng `Repository`/`Entity` của module khác.
3. **Không dùng quan hệ JPA xuyên module** (`@ManyToOne`, `@OneToMany`... trỏ sang entity ở
   module khác). Muốn tham chiếu, lưu ID kiểu nguyên thuỷ (`Long specialtyId`) — coi như
   "khoá ngoại mềm", validate sự tồn tại bằng cách gọi `Service` public của module kia.

> **Vì sao?** Để sau này (nếu cần) tách 1 module thành microservice riêng, việc tách không
> đòi hỏi viết lại logic — chỉ cần thay lời gọi Service trực tiếp bằng lời gọi HTTP/message
> queue. Quy tắc này cũng giúp nhiều người làm song song nhiều module mà không đụng code
> nhau.

### Spring Modulith — CHƯA bật

Dự án CHƯA thêm `spring-modulith-starter-core` (xem
[docs/setup/DECISIONS.md](docs/setup/DECISIONS.md) mục 4) vì lúc dựng khung chỉ có 1 module
đầy đủ — `ApplicationModules.of(...).verify()` sẽ không kiểm chứng được gì. **Khi có module
thứ 2 triển khai xong**, thêm dependency và viết test:

```java
class ModularityTests {
  @Test
  void verifiesModularStructure() {
    ApplicationModules.of(ClinicBookingApiApplication.class).verify();
  }
}
```

## Bảng module

| Module | Nghiệp vụ | Trạng thái |
|---|---|---|
| `identity` | Tài khoản, Role, phân quyền, JWT | Chưa tạo |
| `facility` | Cơ sở, phòng chức năng | Chưa tạo |
| `catalog` | Gói khám/Dịch vụ/Hạng mục (GoiKham/DichVu/HangMucKham), có versioning | Chưa tạo |
| **`doctor`** | Chuyên khoa/Bác sĩ (ChuyenKhoa/BacSi) | **Đầy đủ — module mẫu (Specialty CRUD)** |
| `schedule` | Lịch làm việc, ca, sức chứa slot | Chưa tạo |
| `patient` | Bệnh nhân, thẻ điện tử, tiền sử bệnh | Chưa tạo |
| `booking` | Đặt lịch, Phiếu đặt lịch (QR), hủy/đổi, Waitlist | Chưa tạo |
| `reception` | Tiếp nhận, Phiếu tiếp nhận, phân luồng | Chưa tạo |
| `examination` | Khám, chỉ định phát sinh, kết quả CLS, Phiếu kết quả, đơn thuốc | Chưa tạo |
| `billing` | Tạm ứng, quyết toán, hoàn tiền, hóa đơn | Chưa tạo |
| `violation` | No-show, mã vi phạm, khóa đặt lịch | Chưa tạo |
| `corporate` | Doanh nghiệp, hợp đồng, upload danh sách khám | Chưa tạo |
| `notification` | Email + in-app (KHÔNG dùng Zalo/SMS) | Chưa tạo |
| `reporting` | Báo cáo thống kê (chỉ đọc) | Chưa tạo |
| `shared` | Exception, audit base entity, kiểu dùng chung | **Đầy đủ — nền tảng** |

## Cách thêm 1 module mới

1. Tạo package mới `com.se100.clinic.<tên-module>` (tên lấy đúng theo bảng module ở trên).
2. Mở package `doctor` làm mẫu, copy cấu trúc file (không copy nội dung nghiệp vụ):
   `XxxController`, `XxxService`, `XxxRepository`, `Xxx` (entity), `XxxDtos`,
   `package-info.java`.
3. Viết Flyway migration: `src/main/resources/db/migration/V<yyyyMMdd_HHmm>__<module>_<mô
   tả>.sql` (dùng timestamp, không dùng số tăng dần — tránh đụng version khi nhiều người
   tạo migration song song, xem ví dụ `V20260927_1500__doctor_create_specialty.sql`).
4. Viết unit test (Mockito, ở `src/test/java`) + integration test (Testcontainers, ở
   `src/integrationTest/java`) — theo mẫu ở `doctor`.
5. Nếu module cần dữ liệu từ module khác: gọi `Service` public của module đó, KHÔNG import
   `Repository`/`Entity`.
6. Chạy `./gradlew spotlessApply test` trước khi commit (hoặc để pre-commit hook tự format).

## Commit & branch convention

**Commit message:** [Conventional Commits](https://www.conventionalcommits.org/), scope là
tên module:

```
feat(booking): thêm chức năng waitlist
fix(doctor): sửa lỗi trùng mã chuyên khoa
docs(setup): cập nhật README
build: nâng cấp Spring Boot lên 4.1.1        ← hạ tầng dùng type build/ci/chore, không bắt buộc scope
```

`feat`/`fix` **bắt buộc** phải có scope. Commit-msg hook (Lefthook) tự kiểm tra và chặn nếu
sai định dạng, in ví dụ đúng ngay trong thông báo lỗi.

**Branch naming** (khuyến nghị, KHÔNG bị hook chặn nếu đặt tên khác):

```
<type>/<module>/<mô-tả-ngắn>
feat/booking/waitlist
fix/doctor/duplicate-specialty-code
```

Push thẳng vào `main` BỊ CHẶN bởi pre-push hook — phải tạo branch riêng rồi mở Pull Request.

## Git hooks (Lefthook)

Cài **tự động** khi chạy `./gradlew build` lần đầu (task `installGitHooks`, xem
`build.gradle.kts`) — nếu máy có `npm`. Cài tay:

```bash
npx lefthook install
```

Không có Node.js/npm? Build vẫn chạy bình thường (task chỉ in cảnh báo, không chặn) — cài
Node từ https://nodejs.org rồi chạy lại lệnh trên khi muốn có hook.

| Hook | Làm gì | Có chặn không? |
|---|---|---|
| `pre-commit` | `spotlessApply` tự format code Java rồi tự re-stage | KHÔNG (chỉ tự sửa) |
| `pre-commit` | Quét secret bằng gitleaks (qua Docker image, không cần cài) | CÓ, nếu tìm thấy secret thật. Bỏ qua (không chặn) nếu Docker chưa chạy |
| `commit-msg` | Kiểm tra Conventional Commits | CÓ, nếu sai định dạng |
| `pre-push` | Chặn push thẳng vào `main` | CÓ |
| `pre-push` | `./gradlew test` (unit only) | CÓ, nếu test đỏ |

Cấu hình đầy đủ: [`lefthook.yml`](lefthook.yml), script hỗ trợ ở
[`scripts/lefthook/`](scripts/lefthook/).

## Bảo mật

- **Dữ liệu nhạy cảm** (CCCD/hộ chiếu, tiền sử bệnh, kết quả khám): KHÔNG log ở bất kỳ tầng
  nào, KHÔNG trả trong response lỗi (`GlobalExceptionHandler` chỉ trả message chung cho lỗi
  hệ thống — xem `com.se100.clinic.shared.GlobalExceptionHandler`).
- **Đề xuất mã hoá cột nhạy cảm** (CHƯA triển khai, xem "Quyết định còn mở"): dùng JPA
  `AttributeConverter<String, String>` mã hoá/giải mã tự động khi đọc/ghi entity (ứng viên:
  AES-256-GCM, khoá đọc từ biến môi trường/secret manager — KHÔNG hardcode khoá trong code
  hay migration). Cân nhắc thêm cột hash (vd. SHA-256 của CCCD) để vẫn tra cứu/so khớp được
  mà không cần giải mã toàn bộ bảng.
- **Secret:** chỉ đọc từ biến môi trường (`.env`, bị `.gitignore` chặn — xem
  `.env.example` để biết danh sách biến cần có). KHÔNG hardcode secret trong code hay
  `application*.yml`.
- **Actuator:** chỉ mở `health` và `info` (`management.endpoints.web.exposure.include` trong
  `application.yml`) — không mở `env`, `beans`, `heapdump`... ra ngoài.
- **CORS:** origin đọc từ biến môi trường `CORS_ALLOWED_ORIGINS`, KHÔNG BAO GIỜ dùng `"*"`
  (xem `com.se100.clinic.config.CorsConfig`).

## FE generate TypeScript type từ OpenAPI

Repo FE (ReactJS, repo riêng) generate type TypeScript trực tiếp từ spec OpenAPI mà backend
này tự sinh, dùng [`openapi-typescript`](https://openapi-ts.dev):

```bash
# Chạy trong repo FE, sau khi backend này đang chạy ở localhost:8080
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```

Mỗi khi API đổi (thêm field, đổi endpoint...), FE chỉ cần chạy lại lệnh trên để đồng bộ
type — không cần định nghĩa tay.

## Cài đặt IntelliJ IDEA

1. **Chọn đúng JDK 25:** File → Project Structure → Project → SDK → chọn JDK 25 (nếu chưa
   có, bấm "Add SDK" → "Download JDK" → chọn version 25).
2. **Cài plugin google-java-format** (để format trong IDE khớp với `spotlessApply`):
   Settings → Plugins → tìm "google-java-format" → Install → restart IDE. Sau đó bật ở
   Settings → Other Settings → google-java-format Settings → tick "Enable".
3. **Chạy/debug:** mở `ClinicBookingApiApplication.java`, bấm nút ▶ (Run) cạnh method
   `main` — IntelliJ tự nhận Gradle project, không cần cấu hình Run Configuration thủ công.
   Debug bằng nút 🐞 bên cạnh.
4. **Chạy test:** click phải vào file test (vd. `SpecialtyServiceTest`) → "Run" — hoặc bấm
   ▶ cạnh từng `@Test` method để chạy riêng lẻ.
5. **Xem OpenAPI/Swagger trong IDE:** không cần plugin riêng — mở
   http://localhost:8080/swagger-ui.html sau khi `bootRun`.

## Bật branch protection cho `main` (làm tay trên GitHub)

Việc này KHÔNG được tự động hoá (ghi lên GitHub) — vào GitHub repo → Settings → Branches →
Add branch protection rule cho `main`, bật:

- [ ] Require a pull request before merging
- [ ] Require approvals — ít nhất **1** review
- [ ] Require status checks to pass before merging — chọn job `build` trong CI workflow
- [ ] Do not allow bypassing the above settings (khuyến nghị, kể cả cho admin)

## Quyết định còn mở

Những vấn đề kiến trúc CHƯA chốt, cả nhóm cần bàn khi triển khai module liên quan:

1. **Khoá slot khi đặt lịch đồng thời** (module `booking`/`schedule`): 2 bệnh nhân cùng đặt
   slot cuối cùng cùng lúc thì xử lý sao? Ứng viên: optimistic locking (`@Version` trên
   entity slot) + retry ở tầng Service, hoặc constraint duy nhất ở DB
   (`unique(schedule_id, slot_index)` cho bookings) kết hợp bắt `DataIntegrityViolationException`
   trả về lỗi "hết chỗ".
2. **Mã hoá CCCD/hộ chiếu** (module `patient`): xem đề xuất sơ bộ ở mục
   [Bảo mật](#bảo-mật) — cần quyết định thuật toán, nơi lưu khoá, và có cần tra cứu theo
   CCCD hay không (ảnh hưởng cách thiết kế cột hash).
3. **Versioning Gói khám** (module `catalog`): theo `docs/de-tai.md`, đổi giá/hạng mục tạo
   "phiên bản gói khám mới, mã phiên bản khác biệt". Cần chốt: version mới là 1 row riêng có
   `parent_package_code` trỏ về bản gốc, hay dùng bảng lịch sử (`package_history`) tách khỏi
   bảng chính? Ảnh hưởng cách `booking` tham chiếu "gói khám tại thời điểm đặt lịch".
