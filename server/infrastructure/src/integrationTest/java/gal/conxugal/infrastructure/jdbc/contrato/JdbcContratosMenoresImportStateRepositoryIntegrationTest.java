package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratosMenoresImportState;
import gal.conxugal.domain.contrato.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.contrato.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcContratosMenoresImportStateRepositoryIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @Inject
  ContratosMenoresImportStateRepository importStateRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
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
    assertThat(storedCoveredThrough(organoId)).isEqualTo(T_ZERO);
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
    assertThat(storedCoveredThrough(organoId)).isEqualTo(T_ZERO);
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

    ContratosMenoresImportState other =
        importStateRepository.findByOrganoId(untouched).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(other.state()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
      softly.assertThat(other.cursorDate()).isNull();
      softly.assertThat(other.coveredThrough()).isEqualTo(T_ZERO);
    });
  }

  @Test
  void reads_every_stored_state_for_the_catalogue() throws Exception {
    OrganoId halfLoaded = insertOrgano("consorcio-x");
    OrganoId loaded = insertOrgano("axencia-y");
    insertOrgano("nunca-importada");
    importStateRepository.insert(ContratosMenoresImportState.startedAt(halfLoaded, T_ZERO));
    importStateRepository.insert(ContratosMenoresImportState.startedAt(loaded, T_ZERO));
    importStateRepository.updateState(loaded, ContratosMenoresImportStatus.COMPLETE);

    List<ContratosMenoresImportState> states = importStateRepository.findAll();

    assertThat(states)
        .extracting(ContratosMenoresImportState::organoId, ContratosMenoresImportState::state)
        .containsExactlyInAnyOrder(
            tuple(halfLoaded, ContratosMenoresImportStatus.INCOMPLETE),
            tuple(loaded, ContratosMenoresImportStatus.COMPLETE));
  }

  private Table importStateTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("contrato_menor_import_state")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("organo_id")})
        .build();
  }

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a
  // timestamptz against the session time zone and the claim here is about the instant.
  private Instant storedCoveredThrough(OrganoId organoId) throws SQLException {
    String sql = "SELECT covered_through FROM contrato_menor_import_state WHERE organo_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, organoId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No state stored for Órgano %s".formatted(organoId));
        }
        return resultSet.getTimestamp("covered_through").toInstant().truncatedTo(ChronoUnit.MILLIS);
      }
    }
  }

  private OrganoId insertOrgano(String sourceKey) throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (Connection connection = dataSource.getConnection();
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
}
