package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The name order both reads promise callers, end to end. The rest of this suite stubs the
 * use cases, which can only show that the controller serves the list it was handed — the
 * order itself is the database's, under the Galician collation, so it takes a real one to
 * assert. This is the one class here that needs a Docker daemon.
 */
// transactional = false: the fixture is written from the test thread but read back over
// HTTP on a server thread with its own connection, which would never see it from inside
// the test's own uncommitted transaction. @AfterEach clears the tables instead.
@MicronautTest(transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganosReadOrderIntegrationTest extends AuthenticationTestSupport
    implements TestPropertyProvider {

  // Deliberately not in name order, so an unordered query cannot pass by luck. Under the
  // C/POSIX collation a cluster may default to, the accented names trail after Zamora and
  // Ñande after Ourense — which is what the declared collation exists to prevent.
  private static final List<String> INSERTION_ORDER =
      List.of("Zamora", "Ñande", "Ávila", "Ourense", "Avión", "Muros");

  private static final List<String> NAME_ORDER =
      List.of("Ávila", "Avión", "Muros", "Ñande", "Ourense", "Zamora");

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        // application-test.yml turns the datasource off for the rest of this suite, which
        // needs no database at all; this class is the exception, so it turns it back on.
        "datasources.default.enabled", "true",
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void catalogue_arrives_in_galician_name_order(RequestSpecification spec) throws Exception {
    for (String name : INSERTION_ORDER) {
      insertOrgano(name);
    }

    Response response = read(spec, "/api/organos");

    assertThat(response.jsonPath().getList("name", String.class))
        .containsExactlyElementsOf(NAME_ORDER);
  }

  @Test
  void taxonomia_arrives_in_galician_name_order(RequestSpecification spec) throws Exception {
    for (String name : INSERTION_ORDER) {
      insertTermo(name);
    }

    Response response = read(spec, "/api/organos/taxonomia");

    assertThat(response.jsonPath().getList("name", String.class))
        .containsExactlyElementsOf(NAME_ORDER);
  }

  private Response read(RequestSpecification spec, String path) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(path);

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }

  private void insertOrgano(String name) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active) "
            + "VALUES (uuidv7(), ?, ?, TRUE)";
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setString(2, name);
      statement.executeUpdate();
    }
  }

  private void insertTermo(String name) throws Exception {
    try (Connection connection = connect();
        PreparedStatement statement =
            connection.prepareStatement("INSERT INTO termo (id, name) VALUES (uuidv7(), ?)")) {
      statement.setString(1, name);
      statement.executeUpdate();
    }
  }

  /**
   * Straight to the container rather than through the injected {@code DataSource}: that one
   * is Micronaut Data's contextual wrapper, which hands out the surrounding transaction's
   * connection — and the request under test runs on its own.
   */
  private static Connection connect() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
