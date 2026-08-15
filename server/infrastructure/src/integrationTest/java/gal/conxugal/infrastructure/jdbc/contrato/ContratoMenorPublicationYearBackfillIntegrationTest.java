package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The one thing every other test in this package cannot see: the schema they meet was migrated
 * before a single contract existed, so they prove the generated column's <em>expression</em> and
 * never its <em>backfill</em>. Production is the other case — the table already holds contratos
 * menores when this migration arrives — and it is the case that would fail loudly and only once.
 *
 * <p>So Flyway is driven by hand rather than by the Micronaut context: to the version before the
 * column exists, then over rows inserted at that version. The assertion is that the years are
 * there afterwards, which no statement in the migration puts there — adding a stored generated
 * column computes it for every existing row, and a backfill written out would be a second answer
 * able to disagree with the first.
 */
@Testcontainers(disabledWithoutDocker = true)
class ContratoMenorPublicationYearBackfillIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  /**
   * The version before the column, which has to be a literal. The second step goes to the latest
   * instead of naming a version: it proves the same backfill and, from here on, that every
   * migration added later still applies over rows that were already stored.
   */
  private static final String BEFORE_THE_COLUMN = "15";

  private static final UUID ORGANO = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final long DATED = 700001L;
  private static final long UNDATED = 700002L;

  private static final String SEED_ORGANO =
      """
      INSERT INTO organo_contratacion (id, source_key, name, active)
      VALUES (?, 'consorcio-x', 'consorcio-x', TRUE)
      """;

  private static final String SEED_CONTRACTS =
      """
      INSERT INTO contrato_menor (source_id, organo_id, publication_date, amount)
      VALUES (?, ?, DATE '2024-03-17', 1000), (?, ?, NULL, 1000)
      """;

  @Test
  void the_migration_backfills_every_contract_already_stored_and_leaves_the_undated_one_null()
      throws Exception {
    migrateTo(MigrationVersion.fromVersion(BEFORE_THE_COLUMN));
    try (Connection connection = openConnection()) {
      execute(connection, SEED_ORGANO, ORGANO);
      execute(connection, SEED_CONTRACTS, DATED, ORGANO, UNDATED, ORGANO);

      migrateTo(MigrationVersion.LATEST);

      assertThat(publicationYearOf(connection, DATED)).isEqualTo(2024);
      assertThat(publicationYearOf(connection, UNDATED)).isNull();
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

  private static Integer publicationYearOf(Connection connection, long sourceId)
      throws SQLException {
    String sql = "SELECT publication_year FROM contrato_menor WHERE source_id = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, sourceId);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException(
              "No contract stored under source id %d".formatted(sourceId));
        }
        int year = rows.getInt("publication_year");
        return rows.wasNull() ? null : year;
      }
    }
  }
}
