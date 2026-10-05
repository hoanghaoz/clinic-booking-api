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
- **Quy ước API chung với FE** (dạng response, phân trang/lọc/sắp xếp, mã lỗi) — bắt buộc đọc
  trước khi viết endpoint đầu tiên:
  [`docs/conventions/api-conventions.md`](docs/conventions/api-conventions.md).

## Mục lục

- [Chạy local từng bước](#chạy-local-từng-bước)
- [Danh sách lệnh hay dùng](#danh-sách-lệnh-hay-dùng)
- [Kiến trúc & quy tắc module](#kiến-trúc--quy-tắc-module)
- [Bảng module](#bảng-module)
- [Cách thêm 1 module mới](#cách-thêm-1-module-mới)
- [Quy ước API (tóm tắt)](#quy-ước-api-tóm-tắt)
- [Commit & branch convention](#commit--branch-convention)
- [Git hooks (Lefthook)](#git-hooks-lefthook)
- [Bảo mật](#bảo-mật)
- [FE generate TypeScript type từ OpenAPI](#fe-generate-typescript-type-từ-openapi)
- [Xử lý sự cố thường gặp](#xử-lý-sự-cố-thường-gặp)
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
   > ⚠️ `.env` **chỉ được `docker compose` đọc** (để đặt cổng/mật khẩu cho Postgres), còn
   > `./gradlew bootRun` **KHÔNG tự đọc** file này. Nếu bạn đổi giá trị trong `.env` (vd. `DB_PORT`),
   > phải đưa cùng giá trị đó cho app: `export DB_PORT=5433` trước khi `bootRun`, hoặc đặt
   > Environment variables trong Run Configuration của IntelliJ.

3. **Bật Postgres + Mailpit:**
   ```bash
   docker compose up -d
   ```
   - Postgres: `localhost:5432` (user/pass mặc định: `clinic`/`clinic`, DB `clinic_booking`).
   - Mailpit Web UI (xem email "đã gửi"): http://localhost:8025
   - Cổng 5432 đã bị chiếm (vd. máy đang có Postgres khác)? Xem
     [Xử lý sự cố thường gặp](#xử-lý-sự-cố-thường-gặp).

4. **Build & chạy app:**
   ```bash
   ./gradlew bootRun
   ```
   Lần đầu chạy sẽ tự tải Gradle distribution + toàn bộ dependency (có thể mất vài phút tuỳ
   mạng). Flyway tự chạy migration khi app khởi động (xem log `Successfully applied N
   migration`). Git hooks KHÔNG cài ở bước này — chúng chỉ được cài bởi `./gradlew build`
   hoặc `npx lefthook install` (xem [Git hooks (Lefthook)](#git-hooks-lefthook)).

5. **Kiểm tra:**
   - Health check: http://localhost:8080/actuator/health → JSON có `"status":"UP"`
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI spec (JSON): http://localhost:8080/v3/api-docs
   - Thử API mẫu: `curl "http://localhost:8080/api/v1/specialties?page=1&size=10&sort=-createdAt"`
     → `{"data": [...], "meta": {"page":1,"size":10,"totalElements":0,"totalPages":0}}`

## Danh sách lệnh hay dùng

| Lệnh | Mục đích |
|---|---|
| `./gradlew bootRun` | Chạy app (profile mặc định: `dev`), dùng Postgres của `docker compose` |
| `./gradlew bootTestRun` | Chạy app với Postgres tự khởi bằng Testcontainers (cần Docker, KHÔNG cần `docker compose up`, không đụng cổng 5432) |
| `./gradlew build` | Build đầy đủ: compile + unit test + package jar, rồi cài Git hooks (cần `npm`) |
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
├── shared/    ← ApiResult/PageParams, exception + ErrorCode, audit entity, Role — module khác
│                được import trực tiếp
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
| **`doctor`** | Chuyên khoa/Bác sĩ (ChuyenKhoa/BacSi) | **Đầy đủ — module mẫu (Specialty CRUD + phân trang/lọc/sắp xếp + mã lỗi riêng)** |
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
| `shared` | Response wrapper, phân trang, exception/mã lỗi, audit base entity, kiểu dùng chung | **Đầy đủ — nền tảng** |

## Cách thêm 1 module mới

1. Tạo package mới `com.se100.clinic.<tên-module>` (tên lấy đúng theo bảng module ở trên).
2. Mở package `doctor` làm mẫu, copy cấu trúc file (không copy nội dung nghiệp vụ):
   `XxxController`, `XxxService`, `XxxRepository`, `Xxx` (entity), `XxxDtos`,
   `XxxErrorCode` (mã lỗi nghiệp vụ của module), `package-info.java`.
3. Viết Flyway migration: `src/main/resources/db/migration/V<yyyyMMdd_HHmm>__<module>_<mô
   tả>.sql` (dùng timestamp, không dùng số tăng dần — tránh đụng version khi nhiều người
   tạo migration song song, xem ví dụ `V20260927_1500__doctor_create_specialty.sql`).
4. Viết test theo mẫu ở `doctor`: unit test Service (Mockito) + test hợp đồng HTTP của
   Controller (MockMvc) ở `src/test/java`, và integration test (Testcontainers, kế thừa
   `AbstractIntegrationTest`) ở `src/integrationTest/java`. Danh sách test cần có: xem
   [`docs/conventions/api-conventions.md`](docs/conventions/api-conventions.md) mục 5.
5. Nếu module cần dữ liệu từ module khác: gọi `Service` public của module đó, KHÔNG import
   `Repository`/`Entity`.
6. Controller trả `ApiResult<...>`, danh sách dùng `PageParams`, lỗi nghiệp vụ ném `BusinessException`
   với mã trong `XxxErrorCode` — theo
   [quy ước API](docs/conventions/api-conventions.md) (có checklist ở mục 6).
7. Chạy `./gradlew spotlessApply test integrationTest` trước khi mở PR (pre-commit hook chỉ tự
   format, `pre-push` chỉ chạy unit test).

## Quy ước API (tóm tắt)

Đầy đủ + ví dụ + cách áp dụng: [`docs/conventions/api-conventions.md`](docs/conventions/api-conventions.md).

- **Thành công:** `{"data": ...}`; danh sách thêm `"meta": {page, size, totalElements, totalPages}`.
  Tạo mới → `201` + `Location`; xoá → `204` không body.
- **Phân trang:** `page` bắt đầu từ **1** (mặc định 1), `size` mặc định **20**, tối đa **100**,
  `sort=-createdAt,name` (`-` = giảm dần, chỉ field được phép). Tham số sai → `400`, không tự sửa.
- **Lỗi:** `application/problem+json` có `code` (FE rẽ nhánh theo `code`, không theo `detail`),
  lỗi theo field có `errors: [{field, message}]`. Lỗi DB constraint → 409/400 có `code`, không bao giờ
  500 chung.

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

## Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `docker compose up` báo `address already in use` / Postgres không lên (cổng 5432) | Máy đã có Postgres khác giữ cổng. Đổi cổng cho **cả Postgres lẫn app**: `DB_PORT=5433 docker compose up -d` rồi `DB_PORT=5433 ./gradlew bootRun`. Hoặc dùng `./gradlew bootTestRun` (Postgres Testcontainers, cổng ngẫu nhiên) |
| Sửa `.env` mà app không đổi hành vi | `.env` chỉ `docker compose` đọc; app đọc **biến môi trường** của shell/IDE — xem bước 2 ở [Chạy local](#chạy-local-từng-bước) |
| `Connection refused` tới `localhost:5432` lúc `bootRun` | Chưa `docker compose up -d`, hoặc Postgres đang dùng cổng khác (xem dòng đầu) |
| `./gradlew integrationTest` lỗi `Could not find a valid Docker environment` | Docker daemon chưa chạy (Docker Desktop/`systemctl start docker`). `./gradlew test` thì không cần Docker |
| Swagger UI 404 / trống | Swagger UI chỉ bật ở profile `dev` (mặc định); profile `test` tắt UI, profile `prod` tắt cả UI lẫn `/v3/api-docs` — có chủ đích |
| FE nhận `400` khi gọi danh sách với `size=500` | Đúng thiết kế: `size` tối đa 100 và bị **từ chối** chứ không tự cắt. Xem [quy ước phân trang](docs/conventions/api-conventions.md#2-pagination-filter-sort) |
| Push bị chặn `Không được push thẳng vào nhánh 'main'` | Hook `pre-push`. Tạo branch riêng rồi mở PR (xem [Commit & branch convention](#commit--branch-convention)) |

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
   trả về lỗi "hết chỗ". Phần hạ tầng đã có sẵn: `DbErrors` để nhận ra loại vi phạm và
   `GlobalExceptionHandler` đã đổi unique/exclusion/`@Version` thành `409` (xem
   [mục 3.6](docs/conventions/api-conventions.md#36-lỗi-từ-constraint-db)); module `booking` chỉ
   cần dịch sang mã riêng `SLOT_FULL`.
2. **Mã hoá CCCD/hộ chiếu** (module `patient`): xem đề xuất sơ bộ ở mục
   [Bảo mật](#bảo-mật) — cần quyết định thuật toán, nơi lưu khoá, và có cần tra cứu theo
   CCCD hay không (ảnh hưởng cách thiết kế cột hash).
3. **Versioning Gói khám** (module `catalog`): theo `docs/de-tai.md`, đổi giá/hạng mục tạo
   "phiên bản gói khám mới, mã phiên bản khác biệt". Cần chốt: version mới là 1 row riêng có
   `parent_package_code` trỏ về bản gốc, hay dùng bảng lịch sử (`package_history`) tách khỏi
   bảng chính? Ảnh hưởng cách `booking` tham chiếu "gói khám tại thời điểm đặt lịch".
