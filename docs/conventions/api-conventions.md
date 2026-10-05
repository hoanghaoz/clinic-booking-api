# Quy ước API (backend ↔ frontend)

Tài liệu này là **hợp đồng chung** giữa backend và frontend, và là checklist cho mọi thành viên
khi viết endpoint mới. Module mẫu là `com.se100.clinic.doctor` (API Chuyên khoa
`/api/v1/specialties`) — khi viết feature mới, **copy cách làm ở đó** rồi đối chiếu tài liệu này.

Mọi quy ước dưới đây đều được test tự động giữ lại (xem [mục 5](#5-test-cần-viết-cho-endpoint-mới)):
nếu bạn đổi hành vi mà test đỏ, nghĩa là bạn đang đổi hợp đồng với FE — bàn với cả nhóm trước.

## Tóm tắt 1 phút

| Chủ đề | Quy ước |
|---|---|
| Response thành công | Luôn bọc trong `{"data": ...}`; danh sách thêm `"meta"` (phân trang). Kiểu Java: `ApiResult<T>` |
| Tạo mới | `201` + header `Location` + `{"data": {...}}` |
| Xoá / ngưng hoạt động | `204`, **không có body** |
| Phân trang | `page` (bắt đầu từ **1**, mặc định 1), `size` (mặc định **20**, tối đa **100**) |
| Sắp xếp | `sort=-createdAt,name` (dấu `-` = giảm dần), chỉ field được phép |
| Lọc | tham số riêng cho từng endpoint, vd `keyword`, `active` |
| Tham số sai | `400` + `VALIDATION_ERROR`, **không** tự sửa/cắt giá trị |
| Response lỗi | RFC 9457 `ProblemDetail` + `code` (mã máy đọc được) + `errors[]` (khi lỗi theo field) |
| FE nhận biết lỗi | Dựa vào `code` (và HTTP status), **không** parse `detail` |
| Lỗi DB constraint | Luôn ra 4xx có `code`, không bao giờ 500 chung |

---

## 1. Response thành công

### 1.1. Một đối tượng

```json
HTTP/1.1 200 OK
{
  "data": { "id": 1, "code": "NOI", "name": "Nội tổng quát", "active": true, "createdAt": "2026-10-05T14:29:42.767047Z" }
}
```

### 1.2. Danh sách (có phân trang)

```json
HTTP/1.1 200 OK
{
  "data": [ { "id": 3, "code": "TIM" }, { "id": 1, "code": "NOI" } ],
  "meta": { "page": 1, "size": 2, "totalElements": 3, "totalPages": 2 }
}
```

### 1.3. Bảng status

| Thao tác | Status | Body |
|---|---|---|
| `GET` 1 hoặc nhiều | `200` | `ApiResult` (danh sách có `meta`) |
| `POST` tạo mới | `201` + `Location: /api/v1/<resource>/<id>` | `ApiResult` chứa bản ghi vừa tạo |
| `PUT`/`PATCH` | `200` | `ApiResult` chứa bản ghi sau khi sửa |
| `DELETE` | **`204`** | **Không có body** (FE không được `.json()` response này) |
| Hành động nghiệp vụ không trả dữ liệu (vd. huỷ lịch) | `204` | Không có body |
| Hành động nghiệp vụ có trả dữ liệu (vd. đổi lịch) | `200` | `ApiResult` |

> Vì sao `DELETE` là 204 mà không bọc wrapper? Xoá ở dự án này là xoá mềm (`active=false`),
> FE không cần dữ liệu trả về; 204 nói rõ "thành công, không có gì để đọc". Nếu một thao tác
> *cần* trả dữ liệu (vd. trả bản ghi sau khi ngưng) thì dùng `200` + `ApiResult`, đừng trả `204` kèm body.

### 1.4. Quy tắc

- **Không bao giờ** trả mảng trần (`[...]`) hay object trần — luôn có `data`. Nhờ vậy sau này
  thêm `meta`/`links` mà không phá FE.
- `meta` chỉ có ở danh sách phân trang. Với object đơn, key `meta` **không xuất hiện** (không phải `null`).
- Thời gian là `Instant` → chuỗi ISO-8601 UTC kết thúc bằng `Z`. Hiển thị theo múi giờ
  `Asia/Ho_Chi_Minh` là việc của FE.
- Field không có giá trị trả `null` (không bỏ key) — trừ `meta` như trên.
- Response DTO là `record` trong `XxxDtos`; **không** trả Entity ra ngoài.
- Khai báo `@Schema(requiredProperties = {...})` trên response DTO với các field luôn có
  giá trị, để type TypeScript FE sinh ra là `id: number` chứ không phải `id?: number`.

### 1.5. Cách viết trong code

```java
// Controller — chỉ bọc, không có logic
@GetMapping("/{id}")
ApiResult<SpecialtyResponse> getById(@PathVariable Long id) {
  return ApiResult.of(specialtyService.getById(id));
}

@PostMapping
ResponseEntity<ApiResult<SpecialtyResponse>> create(@Valid @RequestBody CreateSpecialtyRequest request) {
  SpecialtyResponse created = specialtyService.create(request);
  return ResponseEntity.created(URI.create("/api/v1/specialties/" + created.id()))
      .body(ApiResult.of(created));
}

@DeleteMapping("/{id}")
ResponseEntity<Void> deactivate(@PathVariable Long id) {
  specialtyService.deactivate(id);
  return ResponseEntity.noContent().build();
}
```

`Service` trả DTO thường (hoặc `Page<Dto>` cho danh sách), **không** biết gì về `ApiResult`/HTTP.

---

## 2. Pagination, filter, sort

Áp dụng cho **mọi** endpoint trả danh sách có thể lớn (bệnh nhân, lịch hẹn, bác sĩ...). Danh sách
chắc chắn nhỏ và cố định (vd. 5 `Role`) có thể không phân trang, nhưng vẫn bọc `{"data": [...]}`.

### 2.1. Tham số

| Query | Ý nghĩa | Mặc định | Giới hạn |
|---|---|---|---|
| `page` | Số trang, **bắt đầu từ 1** | `1` | `>= 1` |
| `size` | Số phần tử mỗi trang | `20` | `1..100` |
| `sort` | Các field sắp xếp, phân tách bằng dấu phẩy; tiền tố `-` = giảm dần | do endpoint chọn | chỉ field trong whitelist của endpoint |

Ví dụ: `GET /api/v1/specialties?page=2&size=10&sort=-createdAt,name&keyword=tim&active=true`

- Chọn `page` bắt đầu từ 1 vì FE hiển thị "Trang 1" — `meta.page` trả đúng số FE gửi lên, không phải quy đổi.
  (Spring nội bộ đánh số từ 0; `PageParams` và `ApiResult.of(Page)` tự quy đổi, đừng tự trừ/cộng 1 ở nơi khác.)
- `sort` viết được cả dạng lặp tham số: `sort=-createdAt&sort=name`.
- Sắp xếp luôn được **ổn định**: server tự thêm `id` tăng dần làm khoá cuối, nên 2 bản ghi trùng
  giá trị sort không bị lặp/thiếu giữa các trang.
- Tên field sort **trùng tên field trong response** (vd. `createdAt`), không phải tên cột DB.

### 2.2. Metadata của response danh sách

```json
"meta": { "page": 2, "size": 10, "totalElements": 53, "totalPages": 6 }
```

| Field | Ý nghĩa |
|---|---|
| `page` | Trang hiện tại (1-based) — đúng giá trị đã yêu cầu |
| `size` | Kích thước trang thực tế |
| `totalElements` | Tổng số bản ghi khớp filter (trên mọi trang) |
| `totalPages` | `ceil(totalElements / size)`; `0` khi không có bản ghi nào |

FE tự suy ra "có trang sau" bằng `page < totalPages`.

### 2.3. Filter

- Mỗi endpoint tự khai báo filter của mình bằng `@RequestParam` riêng (tên camelCase, trùng tên field response nếu lọc theo field).
- Tên chung nên dùng: `keyword` (tìm chuỗi "chứa", không phân biệt hoa thường, trên các field text do endpoint
  chọn — ghi rõ trong Swagger), `active` (boolean), `status` (enum). Khoảng ngày/giờ: `fromDate`/`toDate`
  (hoặc `from`/`to` cho `Instant`) — **bao gồm cả 2 đầu**, định dạng ISO-8601.
- **Mọi filter đều tuỳ chọn**; không gửi = không lọc. Nhiều filter kết hợp bằng `AND`.
- Ký tự `%`, `_` trong `keyword` được coi là **ký tự thường**, không phải wildcard SQL (đã escape).
- Tham số **không biết** (vd. `?foo=bar`) bị **bỏ qua**, không báo lỗi — nên đừng đặt sai tên filter mà tưởng là đã lọc.

### 2.4. Cách xử lý tham số không hợp lệ

Nguyên tắc: **từ chối rõ ràng, không tự sửa** (không "kẹp" `size=500` về 100) để FE không đọc nhầm `meta`.

| Request | Kết quả |
|---|---|
| `page=0`, `page=-1` | `400 VALIDATION_ERROR`, `errors[]` có `field: "page"` |
| `size=0`, `size=101` | `400 VALIDATION_ERROR`, `errors[]` có `field: "size"` |
| `page=abc`, `size=x`, `active=maybe` | `400 VALIDATION_ERROR`, `field` = tên tham số |
| `sort=password` (field không cho phép) | `400 VALIDATION_ERROR`, `field: "sort"`, message liệt kê các field cho phép |
| `sort=name,-name` (lặp field) | `400 VALIDATION_ERROR`, `field: "sort"` |
| Nhiều lỗi cùng lúc | `400`, `errors[]` liệt kê **tất cả** |
| `page` vượt quá trang cuối | **`200`**, `data: []`, `meta` vẫn đúng (không phải lỗi) |
| Không có bản ghi nào khớp | `200`, `data: []`, `meta.totalElements: 0`, `meta.totalPages: 0` |

### 2.5. Cách áp dụng cho feature mới (theo mẫu `doctor`)

**① Repository** — thêm `JpaSpecificationExecutor` để ghép filter tuỳ chọn:

```java
interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {}
```

**② Service** — khai báo field được sort + sort mặc định, dựng điều kiện lọc, nhận `Pageable`:

```java
public static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "createdAt");
public static final Sort DEFAULT_SORT = Sort.by("fullName");

@Transactional(readOnly = true)
public Page<PatientResponse> list(String keyword, Boolean active, Pageable pageable) {
  return patientRepository.findAll(matching(keyword, active), pageable).map(PatientResponse::from);
}
```

`matching(...)` là `Specification` chỉ thêm điều kiện khi tham số có giá trị — copy từ
`SpecialtyService#matching` (đã xử lý escape `%`/`_`). Chỉ cho sort theo field **có index** hoặc bảng nhỏ.

**③ Controller** — nhận `PageParams` (annotation `@ParameterObject` để Swagger hiện đủ `page/size/sort`):

```java
@GetMapping
ApiResult<List<PatientResponse>> list(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) Boolean active,
    @ParameterObject PageParams pageParams) {
  var pageable = pageParams.toPageable(PatientService.SORTABLE_FIELDS, PatientService.DEFAULT_SORT);
  return ApiResult.of(patientService.list(keyword, active, pageable));
}
```

`PageParams.toPageable` đã làm toàn bộ validate ở bảng 2.4 — không tự viết lại.

---

## 3. Lỗi

### 3.1. Hình dạng response lỗi

Mọi lỗi (do code ném, do Spring, do DB) đều trả `Content-Type: application/problem+json` theo
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) kèm `code`:

```json
HTTP/1.1 409 Conflict
{
  "title": "Conflict",
  "status": 409,
  "detail": "Mã chuyên khoa đã tồn tại: NOI",
  "instance": "/api/v1/specialties",
  "code": "SPECIALTY_CODE_EXISTS"
}
```

| Field | Luôn có | Ý nghĩa |
|---|---|---|
| `status` | ✔ | HTTP status (lặp lại trong body) |
| `code` | ✔ | **Mã lỗi máy đọc được — FE rẽ nhánh theo field này** |
| `detail` | ✔ | Câu tiếng Việt cho người đọc; có thể hiển thị toast nhưng **không** parse/so sánh chuỗi |
| `title`, `instance` | ✔ | Tên status; đường dẫn request |
| `errors` | chỉ khi `VALIDATION_ERROR` | Danh sách lỗi theo field (3.3) |

### 3.2. FE nhận biết lỗi bằng cách nào

1. Nhánh theo **`code`** cho lỗi nghiệp vụ cần xử lý riêng (vd. `SLOT_FULL` → tải lại slot,
   `SPECIALTY_CODE_EXISTS` → báo trùng mã).
2. Nhánh theo **HTTP status** cho phản ứng chung (401 → về trang đăng nhập, 5xx → toast "thử lại sau").
3. `VALIDATION_ERROR` → gắn `errors[].message` vào đúng ô nhập theo `errors[].field`.
4. Mã lạ chưa biết → hiển thị `detail`.

```ts
// Type sinh từ OpenAPI: components['schemas']['ApiError']
if (err.code === 'VALIDATION_ERROR') err.errors?.forEach(e => form.setError(e.field, e.message));
else if (err.code === 'SLOT_FULL') reloadSlots();
else toast(err.detail);
```

### 3.3. Cấu trúc lỗi validation

Status **400**, `code: "VALIDATION_ERROR"`, `errors` là mảng `{field, message}` (mỗi field có thể xuất hiện nhiều lần nếu vi phạm nhiều luật):

```json
HTTP/1.1 400 Bad Request
{
  "status": 400, "code": "VALIDATION_ERROR", "detail": "Dữ liệu gửi lên không hợp lệ",
  "errors": [
    { "field": "code", "message": "Mã chuyên khoa không được để trống" },
    { "field": "name", "message": "Tên chuyên khoa không được để trống" }
  ]
}
```

Quy tắc dùng chung cho **mọi** lỗi 400 có chỉ ra tham số/field cụ thể — lỗi trong body (`@Valid`), tham số
query sai kiểu/sai khoảng (`page`, `size`, `sort`, `active`...). `field` là tên property trong body JSON
hoặc tên query param. Thứ tự các phần tử trong `errors` **không** được bảo đảm.
Với lỗi tham số kiểu sai (vd. `page=abc`), `message` chỉ nói "Giá trị không hợp lệ" mà không lặp lại giá trị đã gửi.

### 3.4. Bảng mã lỗi dùng chung (`CommonErrorCode`)

| `code` | Status | Khi nào |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Body/tham số sai ở field cụ thể — có `errors[]` |
| `INVALID_REQUEST` | 400 | 400 không gắn với field: JSON hỏng/sai kiểu, thiếu tham số bắt buộc, vi phạm NOT NULL/CHECK ở DB |
| `NOT_FOUND` | 404 | Không có bản ghi (khi module chưa có mã riêng) hoặc URL không tồn tại |
| `METHOD_NOT_ALLOWED` | 405 | Sai HTTP method |
| `NOT_ACCEPTABLE` | 406 | `Accept` không được hỗ trợ |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` không được hỗ trợ |
| `CONFLICT` | 409 | Xung đột trạng thái chung: sửa đồng thời (`@Version`), exclusion constraint |
| `DUPLICATE_RESOURCE` | 409 | Vi phạm unique ở DB mà service không dịch ra mã riêng |
| `REFERENCE_VIOLATION` | 409 | Vi phạm khoá ngoại ở DB |
| `DATA_INTEGRITY_VIOLATION` | 409 | Ràng buộc toàn vẹn DB khác |
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước — body chỉ có câu chung, chi tiết chỉ nằm trong log server |

`401`/`403` do Spring Security trả; sẽ được chuẩn hoá cùng định dạng khi làm module `identity`
(thêm `UNAUTHORIZED`/`FORBIDDEN` vào `CommonErrorCode`).

### 3.5. Mã lỗi nghiệp vụ của từng module

Mỗi module có **1 enum package-private** implement `ErrorCode`, mỗi hằng số = 1 quy tắc nghiệp vụ mà FE cần nhận ra.
Tên hằng số **chính là** `code` gửi cho FE:

```java
enum BookingErrorCode implements ErrorCode {
  SLOT_FULL(HttpStatus.CONFLICT),
  APPOINTMENT_OVERLAP(HttpStatus.CONFLICT),
  PATIENT_BOOKING_BLOCKED(HttpStatus.FORBIDDEN);
  // field status + constructor + status() — copy từ SpecialtyErrorCode
}

// Trong Service:
throw new ConflictException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ");
throw new NotFoundException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn");
throw new BusinessException(BookingErrorCode.PATIENT_BOOKING_BLOCKED, "Bệnh nhân đang bị khoá đặt lịch");
```

Quy ước:

- Tên: `UPPER_SNAKE_CASE`, mô tả **chuyện gì xảy ra** (`SLOT_FULL`, `SPECIALTY_NOT_FOUND`), **duy nhất** trên toàn hệ thống.
- Status chọn trong enum, **không** set ở Controller/Service. Gợi ý: không tìm thấy → 404; trùng/xung đột trạng thái → 409;
  vi phạm quy tắc nghiệp vụ do trạng thái hiện tại của đối tượng → 409; không đủ quyền → 403.
- `code` là một phần của API: **đổi tên = breaking change** với FE. Thêm mã mới thì không sao.
- Thêm mã mới → ghi vào `@ApiResponse` của endpoint (xem mẫu `SpecialtyController`) để Swagger liệt kê.
- **Không đưa dữ liệu nhạy cảm** (CCCD, tiền sử bệnh...) vào `message` — nó được trả nguyên cho client
  (định danh không nhạy cảm như mã chuyên khoa thì được).
- Ném `BusinessException` (hoặc lớp con `NotFoundException`/`ConflictException`) từ Service;
  **không** tự dựng `ResponseEntity` lỗi trong Controller.

### 3.6. Lỗi từ constraint DB

DB là chốt chặn cuối cùng (2 request cùng qua bước kiểm tra `existsBy...` vẫn chỉ 1 request ghi được).
`GlobalExceptionHandler` dịch `DataIntegrityViolationException` theo SQLSTATE của PostgreSQL, nên **không
bao giờ** rơi vào 500 chung:

| Vi phạm | SQLSTATE | Status | `code` |
|---|---|---|---|
| UNIQUE | `23505` | 409 | `DUPLICATE_RESOURCE` |
| FOREIGN KEY | `23503` | 409 | `REFERENCE_VIOLATION` |
| EXCLUDE (vd. 2 lịch hẹn chồng giờ) | `23P01` | 409 | `CONFLICT` |
| NOT NULL | `23502` | 400 | `INVALID_REQUEST` |
| CHECK | `23514` | 400 | `INVALID_REQUEST` |
| Dữ liệu sai (quá dài, sai định dạng — lớp `22xxx`) | `22001`... | 400 | `INVALID_REQUEST` |
| Loại khác / không rõ | — | 409 | `DATA_INTEGRITY_VIOLATION` |
| Sửa đồng thời (`@Version`, `OptimisticLockingFailureException`) | — | 409 | `CONFLICT` |

Mã chung như `DUPLICATE_RESOURCE` không cho FE biết *cái gì* bị trùng. Khi FE cần biết (đặt lịch hết
chỗ, trùng mã...), **dịch trong Service** sang mã riêng bằng `DbErrors` — đây là mẫu cho `SLOT_FULL`:

```java
try {
  bookingRepository.saveAndFlush(booking);   // saveAndFlush: để lỗi nổ ngay trong try, không đợi commit
} catch (DataIntegrityViolationException e) {
  if (DbErrors.isUniqueViolation(e)) {       // hoặc isExclusionViolation(e), hoặc so DbErrors.constraintName(e)
    throw new ConflictException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ");
  }
  throw e;                                    // loại khác → để handler chung xử lý
}
```

Handler chỉ log **SQLSTATE + tên constraint** (không log message của driver vì nó chứa giá trị cột, có thể là CCCD).

---

## 4. Swagger / OpenAPI

- Swagger UI: http://localhost:8080/swagger-ui.html · spec: http://localhost:8080/v3/api-docs (tắt ở profile `prod`).
- Schema `ApiError` (hình dạng lỗi) và `FieldViolation` được đăng ký tự động; **400 và 500 tự thêm vào mọi endpoint**.
- Ở mỗi endpoint chỉ cần: `@Operation(summary=...)`, `@ApiResponse` cho **lỗi riêng** (404/409/403...) và trạng thái
  thành công không mặc định (`201`, `204`). Dùng hằng số trong `ApiDocs` — copy từ `SpecialtyController`.
- Tham số danh sách dùng `@ParameterObject PageParams`; filter mô tả bằng `@Parameter(description=...)`.
- FE sinh type: `npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts`
  (response thành công là `ApiResult<Tên>` — vd. `ApiResultSpecialtyResponse`, `ApiResultListSpecialtyResponse`).

## 5. Test cần viết cho endpoint mới

Mẫu: các test trong `src/test/.../doctor/` và `src/integrationTest/.../doctor/`.

| Loại | Chạy bằng | Mẫu | Kiểm tra |
|---|---|---|---|
| Unit Service (Mockito) | `./gradlew test` | `SpecialtyServiceTest` | Luật nghiệp vụ, **`ErrorCode` của exception**, dịch lỗi DB → mã riêng |
| Hợp đồng HTTP (MockMvc standalone, mock Service) | `./gradlew test` | `SpecialtyControllerTest` | Status, `data`/`meta`, `Location`, 204 rỗng, 400 `errors[]`, tham số phân trang chuyển đúng sang Service |
| Integration toàn tầng (Postgres thật) | `./gradlew integrationTest` | `SpecialtyControllerIntegrationTest` | Phân trang/sort/filter thật, mã lỗi qua HTTP |
| Lỗi từ constraint DB thật | `./gradlew integrationTest` | `DbConstraintErrorIntegrationTest` | Vi phạm DB → 4xx đúng `code`, không rò rỉ |
| Hợp đồng chung | `test` + `integrationTest` | `PageParamsTest`, `GlobalExceptionHandlerTest`, `ApiResultTest` (unit); `OpenApiIntegrationTest` | Đã có — chỉ sửa khi đổi quy ước chung |

Lưu ý khi viết integration test:

- Kế thừa `AbstractIntegrationTest` (container Postgres dùng chung, không tự khai báo lại).
- **DB dùng chung và không tự dọn** giữa các test → mỗi test tự tạo dữ liệu riêng (mã/tên có hậu tố ngẫu nhiên) và
  lọc danh sách bằng `keyword` đó; **không** giả định bảng rỗng.
- Đọc response danh sách bằng `ParameterizedTypeReference<ApiResult<List<Dto>>>`, lỗi bằng `Map<String,Object>`.

## 6. Checklist thêm endpoint mới

- [ ] Controller package-private, chỉ gọi Service; trả `ApiResult<Dto>` (hoặc `ApiResult<List<Dto>>` cho danh sách), `201` + `Location` khi tạo, `204` khi xoá.
- [ ] Danh sách: `@ParameterObject PageParams`, khai báo `SORTABLE_FIELDS` + `DEFAULT_SORT`, filter tuỳ chọn.
- [ ] Lỗi nghiệp vụ: enum `XxxErrorCode implements ErrorCode`; ném `BusinessException`/`NotFoundException`/`ConflictException` với mã đó.
- [ ] Ràng buộc quan trọng có ở **DB** (unique/FK/check), và Service dịch lỗi DB sang mã riêng nếu FE cần phân biệt.
- [ ] Swagger: `@Operation`, `@ApiResponse` cho 404/409..., mô tả filter; mở `/v3/api-docs` kiểm tra.
- [ ] Test: unit Service + hợp đồng HTTP + integration (mục 5).
- [ ] Không log/trả dữ liệu nhạy cảm trong `message`.
- [ ] Cập nhật tài liệu này nếu bạn đổi quy ước chung.
