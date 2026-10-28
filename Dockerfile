# --- Stage 1: Build the application
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy the build files first so the dependency download layer is cached
# until build.gradle.kts or the wrapper changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
# bootJar, not build: build also runs the tests and tries to install the Lefthook git hooks
RUN ./gradlew --no-daemon bootJar -x test

# ---- Stage 2: run ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system --no-create-home app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
