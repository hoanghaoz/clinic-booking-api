plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.10.3"
	checkstyle
	jacoco
}

group = "com.se100.clinic"
version = "0.0.1-SNAPSHOT"
description = "Clinic appointment booking and medical services API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// Spring Boot 4 tách TestRestTemplate sang module riêng (package org.springframework.boot.resttestclient).
	testImplementation("org.springframework.boot:spring-boot-resttestclient")
	// TestRestTemplate cần RestTemplateBuilder — Boot 4 cũng tách class này sang module riêng.
	testImplementation("org.springframework.boot:spring-boot-restclient")
	// Cần cho TestcontainersConfiguration/TestClinicBookingApiApplication (sinh sẵn bởi
	// Spring Initializr, dùng cho task `bootTestRun` — chạy app local với Postgres thật
	// qua Testcontainers). KHÔNG có nghĩa `./gradlew test` chậm/cần Docker: các class đó
	// không phải @Test, `test` task không động tới chúng — chỉ CÓ MẶT trên classpath.
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// --- Nguồn cho integration test (tách khỏi "test" thường) -----------------
// "test" = unit test, chạy nhanh, KHÔNG cần Docker.
// "integrationTest" = cần Docker (Testcontainers khởi Postgres thật).
sourceSets {
	create("integrationTest") {
		java.srcDir("src/integrationTest/java")
		resources.srcDir("src/integrationTest/resources")
		compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
		runtimeClasspath += output + compileClasspath
	}
}

// NOTE: `by configurations.getting` bị deprecated ở Gradle 9.6+ (chỉ là warning, không lỗi)
// nhưng vẫn là cách NGẮN GỌN NHẤT để vừa lấy Configuration vừa dùng được cú pháp
// `integrationTestImplementation("...")` trong block dependencies{} bên dưới. Chưa đổi sang
// API mới để tránh rủi ro đổi cú pháp không tương thích — cân nhắc lại khi nâng cấp Gradle.
val integrationTestImplementation: Configuration by configurations.getting {
	extendsFrom(configurations.testImplementation.get())
}
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())
// integrationTestImplementation extendsFrom testImplementation (dòng trên) nên đã tự có
// webmvc-test + testcontainers* — không cần khai báo lại dependency riêng ở đây.

val integrationTest =
	tasks.register<Test>("integrationTest") {
		description = "Chạy integration test (dùng Testcontainers — cần Docker đang chạy)."
		group = "verification"
		testClassesDirs = sourceSets["integrationTest"].output.classesDirs
		classpath = sourceSets["integrationTest"].runtimeClasspath
		shouldRunAfter(tasks.test)
		useJUnitPlatform()
	}

// Cố ý KHÔNG cho "check"/"build" phụ thuộc integrationTest: beginner có thể
// build/test nhanh mà không cần bật Docker. Chạy integration test riêng bằng
// `./gradlew integrationTest`.

tasks.withType<Test> {
	useJUnitPlatform()
}

// --- Spotless: tự động format code, không bao giờ chặn commit/build -------
spotless {
	// Không gắn spotlessCheck vào `check`/`build`: code chưa format KHÔNG làm build local đỏ.
	// Pre-commit hook tự format, CI gọi `spotlessCheck` tường minh (xem DECISIONS.md #13).
	isEnforceCheck = false
	java {
		target("src/*/java/**/*.java")
		googleJavaFormat("1.36.1")
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

// --- Checkstyle: CHỈ CẢNH BÁO, không fail build (xem DECISIONS.md #6) -----
checkstyle {
	toolVersion = "14.1.0"
	configFile = file("checkstyle.xml")
	isIgnoreFailures = true
	isShowViolations = true
}

// --- JaCoCo: báo cáo coverage sau khi chạy unit test -----------------------
jacoco {
	toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

// --- Git hooks (Lefthook, cài qua npx) -------------------------------------
// Xem docs/setup/DECISIONS.md #8: dùng npx thay vì tự tải binary theo OS/arch.
// KHÔNG BAO GIỜ được làm build thất bại — nếu máy chưa có Node.js/npm thì chỉ
// in cảnh báo và bỏ qua, đúng nguyên tắc "tooling phải giúp, không được chặn".
val installGitHooks =
	tasks.register<Exec>("installGitHooks") {
		group = "git hooks"
		description = "Cài Lefthook git hooks qua npx (bỏ qua nếu máy chưa có Node.js/npm)."
		isIgnoreExitValue = true
		commandLine(
			"sh",
			"-c",
			"""
			if command -v npx >/dev/null 2>&1; then
			  npx --yes lefthook@2.1.14 install && echo "✅ Đã cài Git hooks (Lefthook)."
			else
			  echo "⚠️  Chưa tìm thấy npm/npx — bỏ qua cài Git hooks."
			  echo "   Cài Node.js (https://nodejs.org) rồi chạy tay: npx lefthook install"
			fi
			""".trimIndent(),
		)
	}

tasks.named("build") {
	finalizedBy(installGitHooks)
}
