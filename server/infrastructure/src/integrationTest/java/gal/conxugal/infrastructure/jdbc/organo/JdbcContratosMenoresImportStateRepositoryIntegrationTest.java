package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// transactional = false, because the three writes take a transaction of their own: inside the
// test's own they would not see the row it had not yet committed, which is neither what a walk
// does nor what the propagation is for.
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcContratosMenoresImportStateRepositoryIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");
  private static final Instant T_ONE = Instant.parse("2026-08-19T22:00:00Z");

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  ContratosMenoresImportStateRepository importStateRepository;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  @Test
  void an_organo_whose_import_never_started_has_no_state_stored() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    Optional<ContratosMenoresImportState> state =
        importStateRepository.findByOrganoId(organoId);

    assertThat(state).isEmpty();
  }

  @Test
  void stores_started_import_as_incomplete_stamped_with_its_first_window() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    importStateRepository.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));

    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("organo_id").isEqualTo(organoId.value());
    assertThat(states).row(0).value("state").isEqualTo("INCOMPLETE");
    assertThat(states).row(0).value("cursor_date").isNull();
  }

  @Test
  void reads_back_the_stored_state_whole() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));

    ContratosMenoresImportState state =
        importStateRepository.findByOrganoId(organoId).orElseThrow();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(state.organoId()).isEqualTo(organoId);
      softly.assertThat(state.state()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
      softly.assertThat(state.cursorDate()).isNull();
      softly.assertThat(state.coveredThrough()).isEqualTo(T_ZERO);
      softly.assertThat(state.refreshedThrough()).isNull();
    });
  }

  // The acceptance criterion this table exists for. Two advances rather than one, because
  // re-stamping T₀ on resumption is the failure being guarded against and a single advance
  // cannot distinguish "carried forward" from "rewritten to the same value".
  @Test
  void advancing_the_cursor_twice_never_moves_the_instant_it_is_covered_through()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));

    importStateRepository.updateCursorDate(organoId, LocalDate.of(2026, 5, 6));
    importStateRepository.updateCursorDate(organoId, LocalDate.of(2026, 2, 6));

    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("cursor_date").isEqualTo(LocalDate.of(2026, 2, 6));
    assertThat(states).row(0).value("state").isEqualTo("INCOMPLETE");
    assertThat(storedInstant(organoId, "covered_through")).isEqualTo(T_ZERO);
  }

  @Test
  void completing_an_import_moves_the_state_and_leaves_the_cursor_and_the_instant_alone()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));
    importStateRepository.updateCursorDate(organoId, LocalDate.of(2018, 1, 1));

    importStateRepository.updateState(organoId, ContratosMenoresImportStatus.COMPLETE);

    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("state").isEqualTo("COMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(LocalDate.of(2018, 1, 1));
    assertThat(storedInstant(organoId, "covered_through")).isEqualTo(T_ZERO);
  }

  // The acceptance criterion T₁ exists as a separate column for: a refresh must not disturb the
  // position a half-loaded Órgano resumes from, nor the mark its future windows fall back to.
  @Test
  void marking_the_refresh_moves_only_that_instant() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));
    importStateRepository.updateCursorDate(organoId, LocalDate.of(2018, 1, 1));

    importStateRepository.updateRefreshedThrough(organoId, T_ONE);

    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("state").isEqualTo("INCOMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(LocalDate.of(2018, 1, 1));
    assertThat(storedInstant(organoId, "covered_through")).isEqualTo(T_ZERO);
    assertThat(storedInstant(organoId, "refreshed_through")).isEqualTo(T_ONE);
  }

  // Every write is scoped to its own Órgano: an unscoped UPDATE would advance a neighbour's
  // cursor, and an import that resumed from another Órgano's position would skip history
  // silently.
  @Test
  void advancing_one_organo_leaves_every_other_organo_untouched() throws Exception {
    OrganoId advanced = insertOrgano("consorcio-x");
    OrganoId untouched = insertOrgano("axencia-y");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(advanced, T_ZERO));
    importStateRepository.insert(ContratosMenoresImportState.startedAt(untouched, T_ZERO));

    importStateRepository.updateCursorDate(advanced, LocalDate.of(2026, 5, 6));
    importStateRepository.updateState(advanced, ContratosMenoresImportStatus.COMPLETE);
    importStateRepository.updateRefreshedThrough(advanced, T_ONE);

    ContratosMenoresImportState other =
        importStateRepository.findByOrganoId(untouched).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(other.state()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
      softly.assertThat(other.cursorDate()).isNull();
      softly.assertThat(other.coveredThrough()).isEqualTo(T_ZERO);
      softly.assertThat(other.refreshedThrough()).isNull();
    });
  }

  private static Table importStateTable() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table("contrato_menor_import_state")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("organo_id")})
        .build();
  }

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a
  // timestamptz against the session time zone and the claim here is about the instant.
  private static @Nullable Instant storedInstant(OrganoId organoId, String column)
      throws SQLException {
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
        Timestamp stored = resultSet.getTimestamp(column);
        return stored == null ? null : stored.toInstant().truncatedTo(ChronoUnit.MILLIS);
      }
    }
  }

  private static OrganoId insertOrgano(String sourceKey) throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, sourceKey);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
