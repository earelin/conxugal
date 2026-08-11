package gal.conxugal.infrastructure.jdbc.support;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL every integration test runs against, declared once. The image tag lives here and
 * nowhere else: spread over one declaration per test class it drifts silently, and a suite where
 * some classes prove the schema against one server version and some against another proves less
 * than it appears to.
 *
 * <p>Each class still owns its own {@code @Container} field, so Testcontainers' own lifecycle is
 * unchanged — one container per class, skipped without a Docker daemon. What is shared is how the
 * container is built and the coordinates it is handed to Micronaut by, not the container itself.
 */
public final class PostgresContainer {

  /**
   * Bump in one place. Alpine because the suite starts a container per test class and the image is
   * pulled on every clean CI run.
   */
  private static final String IMAGE = "postgres:18-alpine";

  private PostgresContainer() {}

  /**
   * A container to declare as a class's {@code @Container} field. Returned rather than started, so
   * a class that needs to configure it further — a fixed username the test asserts on, say — can
   * chain onto it.
   */
  public static PostgreSQLContainer<?> create() {
    return new PostgreSQLContainer<>(IMAGE);
  }

  /**
   * The container's coordinates as Micronaut datasource properties, with Flyway on so the schema
   * under test is the migrated one.
   *
   * <p>It starts the container if nothing has yet: {@code TestPropertyProvider.getProperties()}
   * runs before the Testcontainers JUnit extension does, so the first caller cannot rely on the
   * annotation having started it.
   */
  public static Map<String, String> datasourceProperties(PostgreSQLContainer<?> postgres) {
    return datasourceProperties(postgres, Map.of());
  }

  /**
   * The same coordinates, with {@code overrides} applied over them — for the few classes that need
   * a bounded pool, or that are testing what happens when the schema has <em>not</em> been
   * migrated. A key given here replaces the default of the same name.
   */
  public static Map<String, String> datasourceProperties(
      PostgreSQLContainer<?> postgres, Map<String, String> overrides) {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("datasources.default.url", postgres.getJdbcUrl());
    properties.put("datasources.default.username", postgres.getUsername());
    properties.put("datasources.default.password", postgres.getPassword());
    properties.put("datasources.default.driverClassName", postgres.getDriverClassName());
    properties.put("datasources.default.dialect", "POSTGRES");
    properties.put("flyway.datasources.default.enabled", "true");
    properties.putAll(overrides);
    return properties;
  }
}
