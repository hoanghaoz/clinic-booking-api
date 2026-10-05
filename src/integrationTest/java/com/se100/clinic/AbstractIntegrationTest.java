package com.se100.clinic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every HTTP-level integration test: real Spring context on a random port, real
 * Flyway migrations, real PostgreSQL in Docker. Extend it instead of repeating the annotations.
 *
 * <p>The container is a SINGLETON started once per test JVM (static initializer, no
 * {@code @Container}) and shared by all subclasses — Spring caches the application context between
 * test classes, so a per-class container would leave the cached context pointing at a dead
 * database. Consequence: the DB is shared and NOT cleaned between tests, so each test must use its
 * own unique data (e.g. a random code suffix) and never assume an empty table. Image version is
 * pinned to match docker-compose.yml.
 *
 * <p>{@code @AutoConfigureTestRestTemplate}: since Spring Boot 4 the {@link TestRestTemplate} bean
 * is no longer present in every {@code @SpringBootTest} and must be enabled explicitly.
 */
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-alpine"));

  static {
    POSTGRES.start();
  }

  @Autowired protected TestRestTemplate restTemplate;
}
