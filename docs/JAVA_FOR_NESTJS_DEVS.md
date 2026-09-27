# Java/Spring Boot cho người quen NestJS/ASP.NET Core

Tài liệu này giúp người đã quen NestJS (TypeScript) hoặc ASP.NET Core (C#) đọc hiểu nhanh
codebase Spring Boot của dự án — KHÔNG phải giáo trình Java đầy đủ. Đọc kèm module mẫu
`com.se100.clinic.doctor` (comment trong code giải thích chi tiết từng annotation).

## 1. Bảng ánh xạ khái niệm

| Khái niệm | NestJS (TypeScript) | ASP.NET Core (C#) | Spring Boot (dự án này) |
|---|---|---|---|
| Đơn vị đóng gói nghiệp vụ | `Module` (`@Module()`, khai báo `imports`/`providers`/`controllers`) | thường là 1 "Feature folder" hoặc project riêng trong solution | 1 package Java top-level (vd. `com.se100.clinic.doctor`) — không có class `@Module` tường minh, package + access modifier LÀ ranh giới module |
| Điều hướng HTTP | `@Controller()` + decorator `@Get()/@Post()...` | `[ApiController]` + `[HttpGet]/[HttpPost]...` | `@RestController` + `@GetMapping/@PostMapping...` — package-private trong dự án này |
| Chứa logic nghiệp vụ | `@Injectable()` service | Service class đăng ký trong DI container (`services.AddScoped<T>()`) | `@Service` — PUBLIC, là API duy nhất module khác gọi (quy ước riêng của dự án) |
| Truy vấn DB | `Repository<T>` (TypeORM) hoặc Prisma Client (`prisma.model.findMany()`) | `DbSet<T>` trong `DbContext` (EF Core) | `interface XxxRepository extends JpaRepository<Entity, Id>` — Spring Data tự sinh implementation, package-private trong dự án này |
| Định nghĩa bảng | `@Entity()` (TypeORM) hoặc `model X {}` trong `schema.prisma` | class entity + Fluent API/`[Key]` attribute | `@Entity` + `@Table` — package-private trong dự án này |
| DTO + validate input | class + decorator `class-validator` (`@IsNotEmpty()`...) | class/record + `DataAnnotations` (`[Required]`...) | `record` (Java 16+) + Bean Validation (`jakarta.validation.constraints.*`, vd `@NotBlank`) |
| Dependency Injection | constructor injection mặc định (`constructor(private svc: FooService)`) | constructor injection qua DI container built-in | constructor injection — Spring tự tìm bean theo type, KHÔNG cần Lombok/annotation gì thêm trên field |
| Migration DB | TypeORM migration hoặc `prisma migrate` | EF Core Migrations (`dotnet ef migrations add`) | Flyway — file SQL thuần trong `src/main/resources/db/migration/`, đặt tên `V<timestamp>__<module>_<mô tả>.sql` |
| Middleware xử lý lỗi toàn cục | `ExceptionFilter` (`@Catch()`) | `IExceptionHandler`/middleware `UseExceptionHandler` | `@RestControllerAdvice` + `@ExceptionHandler` — xem `com.se100.clinic.shared.GlobalExceptionHandler`, trả `ProblemDetail` (RFC 9457) |
| Guard/phân quyền route | `Guard` (`@UseGuards(AuthGuard)`, `RolesGuard`) | `[Authorize]` attribute + middleware | `SecurityFilterChain` khai báo tập trung 1 nơi (`com.se100.clinic.config.SecurityConfig`) — không gắn decorator rải rác trên controller |
| Transaction DB | `@Transaction()` (TypeORM) hoặc `prisma.$transaction()` | `using var tx = ...` thủ công, hoặc `[Transactional]` (thư viện ngoài) | `@Transactional` — khai báo trên method, Spring tự mở/commit/rollback |
| Cập nhật entity đã load | luôn gọi tường minh `repo.save(entity)` / `prisma.update()` | luôn gọi `dbContext.SaveChanges()` | Hibernate "dirty checking": sửa field của entity đang managed trong `@Transactional`, Hibernate TỰ phát hiện và UPDATE lúc commit — KHÔNG cần gọi `save()` lại (xem `SpecialtyService#update`) |
| Job định kỳ | `@nestjs/schedule` (`@Cron()`) | `IHostedService`/`BackgroundService` | `@Scheduled(cron = "...")` + `@EnableScheduling` |
| Gửi email | `@nestjs-modules/mailer` | `System.Net.Mail` hoặc thư viện ngoài | `spring-boot-starter-mail` (`JavaMailSender`) |
| Sinh Swagger/OpenAPI | `@nestjs/swagger` (`SwaggerModule.setup()`) | `Swashbuckle`/`NSwag` | springdoc-openapi — tự sinh `/v3/api-docs` chỉ bằng có mặt dependency |
| Build tool | `npm`/`pnpm`/`yarn` + `tsconfig.json` | `dotnet` CLI + `.csproj`/`.sln` | Gradle (Kotlin DSL) qua `./gradlew` — không cần cài Gradle, wrapper tự tải đúng version |
| Package manager / lock file | `package.json` + `package-lock.json` | `.csproj` (`PackageReference`) | `build.gradle.kts` (`dependencies {}` block) — không có "lock file" riêng, version ghim trực tiếp trong `build.gradle.kts` |
| Testing framework | Jest | xUnit/NUnit/MSTest | JUnit 5 + Mockito (unit) + Testcontainers (integration) |
| Access modifier "chỉ trong thư mục này" | không có (chỉ có export/không export ở mức file) | gần nhất là `internal` (phạm vi cả assembly/project) | package-private (không ghi gì trước `class`) — phạm vi hẹp hơn `internal`: CHỈ trong đúng 1 package |

## 2. Khác biệt tư duy quan trọng nhất

1. **Ranh giới module là compiler-enforced, không phải convention.** Ở NestJS, không gì
   ngăn bạn `import { FooRepository } from '../foo/foo.repository'` từ module khác — chỉ có
   quy ước nhóm tự thống nhất. Ở dự án này, `Repository`/`Entity` package-private khiến việc
   import sai **KHÔNG COMPILE ĐƯỢC**, compiler bắt lỗi giúp bạn.
2. **Không cần "interface + implementation" cho mọi Service.** ASP.NET Core thường có
   `IFooService`/`FooService : IFooService` để tiện mock trong test. Spring/Mockito mock
   thẳng được class cụ thể (miễn không phải `final`), nên dự án này không tạo interface
   thừa — `SpecialtyService` không có `ISpecialtyService`.
3. **Entity "managed" tự động lưu khi sửa field (dirty checking).** Xem bảng ở trên, dòng
   "Cập nhật entity đã load" — đây là khác biệt dễ gây bug nhất khi mới chuyển từ
   TypeORM/Prisma/EF Core sang Hibernate nếu không để ý.
4. **`record` thay Lombok.** Dự án CHỦ Ý không dùng Lombok (xem
   `docs/setup/DECISIONS.md` mục 7) để người mới thấy rõ Java sinh constructor/getter/
   `equals`/`hashCode` cho DTO bất biến (`record`) mà không cần "magic" từ thư viện ngoài.

## 3. 10 lệnh `./gradlew` dùng nhiều nhất

| # | Lệnh | Khi nào dùng |
|---|---|---|
| 1 | `./gradlew bootRun` | Chạy app local (tương đương `npm run start:dev` / `dotnet run`) |
| 2 | `./gradlew test` | Chạy unit test nhanh, không cần Docker (tương đương `npm test`/`dotnet test`) |
| 3 | `./gradlew integrationTest` | Chạy integration test (Testcontainers), cần Docker |
| 4 | `./gradlew build` | Build đầy đủ: compile + test + package jar + cài Git hooks |
| 5 | `./gradlew spotlessApply` | Tự format code (tương đương `npm run format` với Prettier) |
| 6 | `./gradlew spotlessCheck` | Kiểm tra format không tự sửa (dùng trong CI) |
| 7 | `./gradlew checkstyleMain` | Xem cảnh báo style cho code ở `src/main` |
| 8 | `./gradlew jacocoTestReport` | Sinh report coverage HTML |
| 9 | `./gradlew clean` | Xoá thư mục `build/` (tương đương xoá `dist/`/`bin/obj`) |
| 10 | `./gradlew tasks` | Liệt kê TẤT CẢ task khả dụng — hữu ích khi quên tên lệnh |
