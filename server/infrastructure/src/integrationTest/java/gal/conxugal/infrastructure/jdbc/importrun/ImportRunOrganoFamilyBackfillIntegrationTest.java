package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What every other test in this package cannot see: the schema they meet was migrated before a
 * single run existed, so they prove the re-keyed table and never the migration <em>over rows
 * already stored</em>. Production is the other case, and it is the one that fails loudly and only
 * once — a {@code NOT NULL} column added to a table that is not empty.
 *
 * <p>So Flyway is driven by hand rather than by the Micronaut context: to the version before the
 * column, then over runs recorded at that version. The backfill is total and unambiguous because
 * every run recorded before this migration is a contratos menores run or a catalogue run, and a
 * catalogue run covers no Órganos — which is why the migration writes one value rather than
 * deriving one per row.
 *
 * <p><strong>Deliberately one test, and it carries no cleanup for that reason.</strong> A second
 * one here would find the schema already at the latest version, so its own {@code migrateTo("17")}
 * would be a no-op and its backfill assertion would pass while testing nothing — and truncating
 * between tests would not help, because what the first test leaves behind is the schema rather
 * than the rows. A second case belongs in a class with a container of its own.
 */
@Testcontainers(disabledWithoutDocker = true)
class ImportRunOrganoFamilyBackfillIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  /**
   * The version before the column, which has to be a literal. The second step goes to the latest
   * instead of naming a version: it proves the same backfill and, from here on, that every
   * migration added later still applies over runs that were already recorded.
   */
  private static final String BEFORE_THE_COLUMN = "17";

  private static final UUID FIRST_ORGANO = UUID.fromString("0192b000-0000-7000-8000-000000000011");
  private static final UUID SECOND_ORGANO = UUID.fromString("0192b000-0000-7000-8000-000000000012");
  private static final UUID RUN = UUID.fromString("0192b000-0000-7000-8000-0000000000ff");

  private static final String SEED_ORGANOS =
      """
      INSERT INTO organo_contratacion (id, source_key, name, active)
      VALUES (?, 'consorcio-x', 'consorcio-x', TRUE), (?, 'axencia-y', 'axencia-y', TRUE)
      """;

  private static final String SEED_RUN =
      """
      INSERT INTO import_run (id, importer, state, started_at, finished_at, last_advanced_at,
                              added, refreshed)
      VALUES (?, 'CONTRATOS_MENORES', 'PARTIALLY_SUCCEEDED', TIMESTAMPTZ '2026-01-15T09:14:02Z',
              TIMESTAMPTZ '2026-01-15T11:40:55Z', TIMESTAMPTZ '2026-01-15T11:40:55Z', 1204, 96)
      """;

  private static final String SEED_COVERAGE =
      """
      INSERT INTO import_run_organo (run_id, organo_id, state, added, refreshed, failure_reason)
      VALUES (?, ?, 'SUCCEEDED', 1204, 96, NULL),
             (?, ?, 'FAILED', 0, 0, 'the source became unreachable')
      """;

  @Test
  void the_migration_reads_every_coverage_row_already_stored_as_contratos_menores()
      throws Exception {
    migrateTo(MigrationVersion.fromVersion(BEFORE_THE_COLUMN));
    try (Connection connection = openConnection()) {
      execute(connection, SEED_ORGANOS, FIRST_ORGANO, SECOND_ORGANO);
      execute(connection, SEED_RUN, RUN);
      execute(connection, SEED_COVERAGE, RUN, FIRST_ORGANO, RUN, SECOND_ORGANO);

      migrateTo(MigrationVersion.LATEST);

      assertThat(coverageOf(connection, RUN))
          .containsExactly(
              "%s|CONTRATOS_MENORES|SUCCEEDED|1204|96".formatted(FIRST_ORGANO),
              "%s|CONTRATOS_MENORES|FAILED|0|0".formatted(SECOND_ORGANO));
    }
  }

  private static void migrateTo(MigrationVersion version) {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static Connection openConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static void execute(Connection connection, String sql, Object... parameters)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  private static List<String> coverageOf(Connection connection, UUID runId) throws SQLException {
    String sql =
        """
        SELECT organo_id, family, state, added, refreshed
          FROM import_run_organo
         WHERE run_id = ?
         ORDER BY organo_id
        """;
    List<String> rows = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, runId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          rows.add(
              "%s|%s|%s|%d|%d"
                  .formatted(
                      resultSet.getObject("organo_id", UUID.class),
                      resultSet.getString("family"),
                      resultSet.getString("state"),
                      resultSet.getInt("added"),
                      resultSet.getInt("refreshed")));
        }
      }
    }
    return rows;
  }
}
