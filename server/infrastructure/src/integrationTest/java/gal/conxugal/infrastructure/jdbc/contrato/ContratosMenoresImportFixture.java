package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The Órgano, the connection and the table handles the contratos menores walk suites set up around,
 * which is everything about them that is neither the walk under test nor what it is asserted on.
 *
 * <p>The suites are two — an initial import and a refresh — and a third is a matter of time, so
 * this exists rather than a third copy of the same hundred lines. What differs between them is only
 * the clock, the source's answers and the claims, all of which stay in the suite that makes them.
 *
 * <p>Every read here goes to the container directly rather than through the injected {@code
 * DataSource}: the suites run with no ambient transaction so that each write under test really
 * commits, and these assertions are about what it committed.
 */
final class ContratosMenoresImportFixture {

  static final String SOURCE_KEY = "242";

  private static final String ORGANO_NAME = "Axencia Turismo de Galicia";

  private final PostgreSQLContainer<?> postgres;

  ContratosMenoresImportFixture(PostgreSQLContainer<?> postgres) {
    this.postgres = postgres;
  }

  /** An active, marked Órgano, which is the only kind either walk is ever handed. */
  OrganoId insertOrgano() throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable)"
            + " VALUES (uuidv7(), ?, ?, TRUE, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, SOURCE_KEY);
      statement.setString(2, ORGANO_NAME);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  static OrganoDeContratacion organo(OrganoId organoId) {
    return new OrganoDeContratacion(organoId, SOURCE_KEY, ORGANO_NAME, true, true, null);
  }

  Table contratoTable() {
    return table("contrato_menor", "source_id");
  }

  Table importStateTable() {
    return table("contrato_menor_import_state", "organo_id");
  }

  Table runTable() {
    return table("import_run", "started_at");
  }

  Table coverageTable() {
    return table("import_run_organo", "organo_id");
  }

  /** Ordered so {@code row(n)} is stable, which is what lets a suite assert every row it set up. */
  Table table(String name, String order) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(order)})
        .build();
  }

  /** The identity a stored contract keeps across a re-read — what refreshing in place means. */
  UUID storedIdentityOf(long sourceId) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM contrato_menor WHERE source_id = ?")) {
      statement.setLong(1, sourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "No contract stored for source id %d".formatted(sourceId));
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  /** The pair of instants on the state row. */
  record StoredMarks(Instant coveredThrough, @Nullable Instant refreshedThrough) {}

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a timestamptz
  // against the session time zone and the claims about these two are about the instants. Both
  // columns come back together so the query stays a literal.
  StoredMarks storedMarks(OrganoId organoId) throws SQLException {
    String sql =
        "SELECT covered_through, refreshed_through FROM contrato_menor_import_state"
            + " WHERE organo_id = ?";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, organoId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No state stored for Órgano %s".formatted(organoId));
        }
        return new StoredMarks(
            instantAt(resultSet, "covered_through"), instantAt(resultSet, "refreshed_through"));
      }
    }
  }

  private static @Nullable Instant instantAt(ResultSet resultSet, String column)
      throws SQLException {
    Timestamp stored = resultSet.getTimestamp(column);
    return stored == null ? null : stored.toInstant().truncatedTo(ChronoUnit.MILLIS);
  }

  void execute(String sql, Object... parameters) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  void truncateEveryTable() throws SQLException {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  private Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
