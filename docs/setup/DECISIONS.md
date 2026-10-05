# Nhật ký quyết định (Decision Log)

Ghi lại những chỗ yêu cầu gốc chưa rõ ràng: đã chọn phương án nào, vì sao, và đã loại
phương án nào. Cập nhật liên tục trong lúc dựng khung repo.

---

### 1. GROUP_ID — chọn tên `<team>`

- **Quyết định:** dùng `com.se100.clinic` (segment `se100`).
- **Lý do:** thư mục repo là `SE100---PP-OOP-`, nhiều khả năng `SE100` là mã môn/mã đồ án.
  Đây là lựa chọn "đọc được" hơn `com.example.clinic` và không cần biết tên nhóm thật.
- **Phương án đã loại:**
  - `com.example.clinic` — quá chung chung, gây nhầm lẫn với boilerplate mặc định của
    Spring Initializr.
  - `com.clinicbooking.api` — không theo đúng mẫu `com.<team>.clinic` mà đề bài yêu cầu.
- Nếu nhóm có tên khác, đổi `groupId`/`packageName` là việc rename gói Java (IDE hỗ trợ
  refactor tự động), không ảnh hưởng kiến trúc.

### 2. Phiên bản Spring Boot / Java

- **Quyết định:** Spring Boot **4.1.1** (release ổn định mới nhất tại 2026-09-27, tra qua
  `start.spring.io/metadata`), Java toolchain **25** (LTS, đã xác nhận có trong danh sách
  `javaVersion` hỗ trợ của Spring Initializr, tức Boot 4.1.1 support Java 25 chính thức).
- **Lý do:** đúng yêu cầu "tra version stable mới nhất... xác nhận hỗ trợ Java 25".
- **Phương án đã loại:** Spring Boot 4.2.0 (còn ở bản M2 — milestone, chưa ổn định);
  Spring Boot 3.5.x (bản cũ hơn, Spring Initializr liệt kê 4.1.1 là bản GA mới nhất nên
  không có lý do dùng bản 3.x).

### 3. springdoc-openapi thêm thủ công

- **Quyết định:** `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`.
- **Lý do:** springdoc không nằm trong danh sách dependency của Spring Initializr nên phải
  thêm tay vào `build.gradle.kts`. README của springdoc xác nhận dòng `3.x` là dòng dành
  cho Spring Boot 4.x / Spring Framework 7 (dòng `2.x` dành cho Boot 3.x).

### 4. Spring Modulith — CHƯA thêm

- **Quyết định:** chưa thêm dependency `spring-modulith-starter-core` vào lần dựng khung
  này, mặc dù đề bài gốc (bản tiếng Việt) có nhắc tới.
- **Lý do:** phiên bản beginner-friendly (bản brief mới nhất) yêu cầu rõ "NOT yet: Spring
  Modulith... enable Modulith once 2+ real modules exist". Lần này chỉ có 1 module đầy đủ
  (`doctor`), 13 module còn lại chỉ có `package-info.java` rỗng — `ApplicationModules.of(...)
  .verify()` sẽ không kiểm chứng được gì có ý nghĩa vì chưa có ràng buộc thực sự giữa các
  module.
- **Khi nào bật lại:** ngay khi có module thứ 2 triển khai đầy đủ (có Service/Repository
  thật), thêm dependency `org.springframework.modulith:spring-modulith-starter-core:2.1.1`
  (bản GA mới nhất tương thích Boot 4.x, tra tại repo1.maven.org — bản `2.2.0` mới chỉ có
  milestone `M2`, chưa GA) + `spring-modulith-starter-test`, rồi viết test
  `ApplicationModules.of(ClinicBookingApiApplication.class).verify()`.
- **Thay thế tạm thời:** không viết test tự động thay thế — access modifier
  package-private của Java đã CHẶN Ở MỨC COMPILE việc module khác import thẳng
  `Repository`/`Entity` (không compile được, không cần test runtime để phát hiện). Giá trị
  thật sự của `ApplicationModules.verify()` là phát hiện PHỤ THUỘC VÒNG giữa API public của
  các module — thứ không có ý nghĩa kiểm chứng khi mới có 1 module đầy đủ. Việc xác nhận
  "import package-private từ module khác → lỗi compile" được làm THỦ CÔNG 1 lần ở Bước 8
  (thêm import sai, quan sát lỗi compile, revert lại) thay vì giữ vĩnh viễn trong test suite.

### 5. OAuth2 Resource Server / JWT — CHƯA thêm

- **Quyết định:** chưa thêm `spring-boot-starter-oauth2-resource-server`. Security hiện tại
  chỉ có enum 5 role (`Role`) và `SecurityFilterChain` cấu hình `permitAll()` toàn bộ trong
  profile `dev`, có `// TODO` rõ ràng.
- **Lý do:** đúng yêu cầu brief mới — JWT thật sẽ làm cùng lúc xây module `identity`
  (module phát hành token), tránh code security nửa vời không ai dùng được.
- **Khi nào bật lại:** khi bắt đầu triển khai đầy đủ module `identity`.

### 6. Checkstyle — chỉ cảnh báo (WARN-ONLY), không fail build

- **Quyết định:** cấu hình Checkstyle Gradle plugin với `ignoreFailures = true` và
  `showViolations = true`; build luôn xanh dù có vi phạm style.
- **Lý do:** brief mới yêu cầu rõ "never fail the build" cho Checkstyle vì nhóm mới học
  Java/Spring — không muốn beginner bị chặn bởi lỗi style khi đang tập trung học logic.
  README có hướng dẫn cách đổi `ignoreFailures = false` khi cả nhóm đã quen.
- **Phương án đã loại:** để Checkstyle fail build ngay từ đầu (đúng thực hành production
  chuẩn, nhưng brief đã đổi ưu tiên sang "learnability over strictness").

### 7. Không dùng Lombok, không dùng H2

- **Quyết định:** giữ nguyên theo brief — code Java thuần (record cho DTO, không dùng
  Lombok để người mới thấy rõ constructor/getter Spring sinh ra thế nào); dùng
  Testcontainers Postgres thay vì H2 cho cả unit lẫn integration test liên quan DB.
- **Lý do:** brief yêu cầu tường minh "No Lombok... No H2".

### 8. Lefthook — cài qua npm, không qua Go/Homebrew/tải binary tay

- **Quyết định:** dùng gói npm `lefthook@2.1.14` (bản mới nhất trên npm registry), thêm
  1 file `package.json` tối giản ở gốc repo chỉ để khai báo `devDependencies.lefthook`,
  và một Gradle task (`installGitHooks`) gọi `npx lefthook install`, gắn vào task `build`
  qua `dependsOn`/`finalizedBy` sao cho lỗi cài hook KHÔNG BAO GIỜ làm build đỏ (chỉ in
  cảnh báo nếu thiếu `npm`).
- **Lý do:**
  - Nhóm chắc chắn có sẵn Node.js/npm vì còn maintain một repo ReactJS riêng — không phát
    sinh dependency mới thực sự.
  - Đã kiểm tra: máy dev hiện tại có sẵn `node v22.23.1` / `npm 10.9.8`, nên cách này chạy
    được thật, không chỉ là lý thuyết.
  - Tránh phải tự viết logic tải binary Lefthook theo OS/arch (Linux/macOS/Windows,
    x86_64/ARM64) trong Gradle — rủi ro bảo mật (thực thi binary tải về khi build) và khó
    bảo trì hơn nhiều so với `npx`.
- **Phương án đã loại:**
  - Gradle plugin cộng đồng `com.fizzpod.lefthook` (plugins.gradle.org) — có tính năng tự
    tải binary đúng OS/arch, nhưng là plugin nhỏ, ít người dùng, DSL cấu hình dạng Groovy
    map khó chuyển sang Kotlin DSL sạch sẽ, và tự thực thi binary tải từ GitHub Releases
    trong quá trình build tiềm ẩn rủi ro chuỗi cung ứng (supply chain) không cần thiết khi
    đã có cách đơn giản hơn (npm) mà nhóm chắc chắn dùng được.
  - Cài Lefthook qua Go/Homebrew — không đảm bảo có sẵn trên máy Windows/mọi thành viên.
  - Dùng `git config core.hooksPath` + script bash thuần (bỏ hẳn Lefthook) — vi phạm yêu
    cầu tường minh "Git hooks (Lefthook)" trong đề bài.

### 9. gitleaks — chạy qua Docker image, không cài binary riêng

- **Quyết định:** hook `pre-commit` gọi gitleaks bằng
  `docker run --rm -v "$PWD":/repo zricethezav/gitleaks:v8.30.1 detect --source /repo ...`
  thay vì cài binary gitleaks vào máy.
- **Lý do:** Docker đã là yêu cầu bắt buộc của dự án (Postgres, Mailpit, Testcontainers) nên
  không phát sinh thêm công cụ mới phải cài. Nếu Docker daemon không chạy lúc commit, hook
  in cảnh báo và **bỏ qua** bước quét (không block) — đúng tinh thần "tooling phải giúp,
  không được chặn" — ngoại trừ trường hợp gitleaks THỰC SỰ quét thấy secret thì mới chặn
  commit (đúng yêu cầu bắt buộc duy nhất được phép chặn ở Bước 5).

### 10. Spring Boot DevTools — chưa thêm

- **Quyết định:** không thêm `spring-boot-devtools` lần này.
- **Lý do:** brief nói "Add nothing advanced that isn't needed yet". DevTools hữu ích
  (auto-restart, LiveReload) nhưng không phải hạ tầng bắt buộc để chạy CRUD mẫu.
- **Khi nào bật lại:** bất kỳ lúc nào cả nhóm thấy vòng lặp sửa-code/chạy-lại chậm, chỉ cần
  thêm `developmentOnly("org.springframework.boot:spring-boot-devtools")`.

### 11. CI — hai job test tách riêng

- **Quyết định:** `./gradlew test` (unit, không cần Docker) chạy trên mọi PR; job
  `integrationTest` (Testcontainers, cần Docker) chạy trên cùng workflow nhưng ở step riêng
  để log lỗi rõ ràng nếu Docker-in-CI có vấn đề, không lẫn với lỗi unit test.
- **Lý do:** đúng yêu cầu Bước 7 "Two test tasks", dễ debug hơn khi tách log.

### 12. `config/` package hạ tầng kỹ thuật, tách khỏi `shared/`

- **Quyết định:** `SecurityConfig`, `CorsConfig`, `OpenApiConfig`, `SchedulingConfig` nằm ở
  package `com.se100.clinic.config`, KHÔNG nằm trong `shared/`.
- **Lý do:** `shared/` theo đề bài là nơi chứa "Exception, audit base entity, kiểu dùng
  chung" — tức là các **thành phần nghiệp vụ dùng lại được**, còn `config/` là cấu hình
  framework thuần tuý (không export API cho module khác gọi). Tách riêng giúp người mới dễ
  phân biệt "code em có thể tái sử dụng" khác với "code cấu hình Spring Boot".

### 13. `spotlessCheck` không gắn vào `./gradlew build`

- **Quyết định:** `spotless { isEnforceCheck = false }`.
- **Lý do:** mặc định Spotless gắn `spotlessCheck` vào `check`, nên code chưa format sẽ làm
  `./gradlew build` local đỏ — trái nguyên tắc "tooling phải giúp, không chặn". Pre-commit
  hook đã tự format trước khi commit, còn CI vẫn gọi `./gradlew spotlessCheck` tường minh.
- **Phương án đã loại:** để mặc định (build local fail vì format).

### 14. Chỉ giữ 1 module mẫu trong `src`, không tạo sẵn folder cho 13 module còn lại

- **Quyết định:** `src/main/java/com/se100/clinic/` chỉ có `shared/`, `config/` (nền tảng) và
  `doctor/` (module mẫu). 13 module còn lại KHÔNG tạo sẵn folder/`package-info.java`.
- **Lý do:** theo yêu cầu của nhóm — repo chỉ dựng base + 1 folder mẫu để cả nhóm biết cấu
  trúc; ai phụ trách module nào tự tạo package theo mẫu `doctor` (xem README "Cách thêm 1
  module mới"). Bảng module trong README vẫn là nguồn tham chiếu tên package.
- **Phương án đã loại:** tạo sẵn 13 package chỉ chứa `package-info.java` (làm trước đó, đã
  xoá).

---

## Quy ước API chung (Task 1 — nền tảng backend)

Hợp đồng đầy đủ nằm ở [`docs/conventions/api-conventions.md`](../conventions/api-conventions.md); phần dưới
chỉ ghi **vì sao** chọn như vậy và phương án đã loại.

### 15. Wrapper response thành công: `ApiResult<T>` = `{data, meta?}`; DELETE giữ 204

- **Quyết định:** mọi response thành công bọc trong `{"data": ...}`, danh sách thêm `"meta"`
  (`ApiResult` trong `shared`). `DELETE` (xoá mềm) giữ `204` **không body**.
- **Lý do:** FE luôn đọc `.data`, thêm metadata sau này không phá FE. Lỗi KHÔNG dùng wrapper này mà dùng
  `ProblemDetail` (RFC 9457) sẵn có — FE phân biệt thành công/lỗi bằng HTTP status, không cần field
  `success: true/false` thừa. DELETE không có dữ liệu để trả nên 204 là đúng ngữ nghĩa; thao tác nào cần
  trả dữ liệu thì dùng `200` + wrapper (ghi trong tài liệu).
- **Phương án đã loại:** wrapper `{success, data, error}` cho cả thành công lẫn lỗi (trùng lặp với
  HTTP status, và phải bỏ `ProblemDetail` đã có); trả mảng/object trần (không mở rộng được);
  DELETE trả `200` + wrapper rỗng (body vô nghĩa).
- **Tên kiểu:** `ApiResult`, không phải `ApiResponse`, để không đụng `@ApiResponse` của Swagger
  (cùng nằm trong controller).

### 16. Phân trang: `page` từ 1, `size` 20 (tối đa 100), từ chối tham số sai

- **Quyết định:** `page` bắt đầu từ **1**; `size` mặc định **20**, tối đa **100**; `sort=-field,field`.
  Tham số sai → `400 VALIDATION_ERROR`, **không** tự kẹp/sửa. `meta` gồm `page, size, totalElements,
  totalPages`. Page vượt cuối trả `200` + `data: []`.
- **Lý do:** FE hiển thị "Trang 1" nên 1-based tránh lỗi lệch 1; từ chối thay vì kẹp để `meta.size` luôn
  đúng cái FE hỏi. `-field` gọn và tránh bẫy của Spring: `sort=name,desc` bị bộ chuyển đổi tách theo dấu phẩy
  thành 2 phần tử. Whitelist field sort tránh lộ field nội bộ (vd. cột nhạy cảm) và sort trên cột không index.
  Tự động thêm `id` làm khoá sort cuối để phân trang ổn định.
- **Cài đặt:** `PageParams` (record `page/size/sort`, bind bằng `@ParameterObject`) tự validate rồi đổi sang
  `Pageable` 0-based của Spring Data — thay vì dùng `Pageable` trần của Spring (page 0-based, tự kẹp size,
  sort kiểu `name,desc`, lỗi định dạng khác chuẩn lỗi của dự án).
- **Phương án đã loại:** `spring.data.web.pageable.one-indexed-parameters` (làm `Page` JSON và `Pageable`
  lệch 1, khó đoán); trả `Page<T>` serialize thẳng (lộ cấu trúc nội bộ của Spring, đổi theo version).
- **Lọc:** dùng `JpaSpecificationExecutor` + `Specification` chỉ thêm điều kiện khi có tham số. Không dùng
  JPQL kiểu `(:kw is null or ...)` vì PostgreSQL không suy ra được kiểu của tham số `null` (lỗi
  `could not determine data type of parameter`/`lower(bytea)`).

### 17. Mã lỗi: `code` trong ProblemDetail, enum `ErrorCode` theo module

- **Quyết định:** mọi response lỗi có property `code`; `BusinessException(ErrorCode, message)` mang theo
  status + mã. Mã dùng chung ở `CommonErrorCode`, mã nghiệp vụ ở enum riêng từng module
  (`SpecialtyErrorCode`, sau này `BookingErrorCode.SLOT_FULL`...). Lỗi theo field là `errors: [{field, message}]`
  với `code=VALIDATION_ERROR`.
- **Lý do:** FE không được parse `detail` (tiếng Việt, đổi tự do). Enum theo module giữ quy tắc
  "module không import nội bộ module khác" và tránh một file mã lỗi khổng lồ gây conflict khi merge.
  `errors` là mảng (không phải map) vì một field có thể vi phạm nhiều luật.
- **Phương án đã loại:** map mã lỗi vào `type` URI của ProblemDetail (FE phải parse URL); một enum chung
  duy nhất trong `shared` (mọi người cùng sửa 1 file).

### 18. Lỗi constraint DB → 4xx có mã, dịch theo SQLSTATE

- **Quyết định:** `GlobalExceptionHandler` bắt `DataIntegrityViolationException`, đọc SQLSTATE
  (`DbErrors`): unique→409 `DUPLICATE_RESOURCE`, FK→409 `REFERENCE_VIOLATION`, exclusion→409 `CONFLICT`,
  not-null/check/lớp `22`→400 `INVALID_REQUEST`, còn lại→409 `DATA_INTEGRITY_VIOLATION`; sửa đồng thời
  (`@Version`) → 409 `CONFLICT`. Service dịch sang mã riêng khi FE cần phân biệt (vd. unique của slot → `SLOT_FULL`).
- **Lý do:** kiểm tra `existsBy...` trước khi ghi không chống được race; DB constraint mới là chốt chặn thật,
  và khi nó nổ thì client phải nhận 4xx dễ hiểu, không phải 500. Chỉ log SQLSTATE + tên constraint vì message
  của driver chứa giá trị cột (có thể là CCCD).
- **Phương án đã loại:** parse tên constraint trong handler chung để ra mã nghiệp vụ (handler phải biết
  nghiệp vụ của mọi module — đặt việc này ở Service của module).

### 19. Integration test: container Postgres singleton + DB dùng chung

- **Quyết định:** `AbstractIntegrationTest` khởi **một** container cho cả lượt chạy (static initializer),
  mọi test HTTP kế thừa nó. DB không tự dọn giữa các test → mỗi test tự tạo dữ liệu có hậu tố ngẫu nhiên.
- **Lý do:** Spring cache application context giữa các class test; với `@Container` static theo class, context
  được cache sẽ trỏ vào container đã bị tắt. Một container cho cả lượt cũng nhanh hơn nhiều.
- **Phương án đã loại:** `@Transactional` rollback sau mỗi test (không áp dụng được: request đi qua HTTP thật
  ở thread khác); `TRUNCATE` sau mỗi test (chậm, dễ quên khi thêm bảng).
