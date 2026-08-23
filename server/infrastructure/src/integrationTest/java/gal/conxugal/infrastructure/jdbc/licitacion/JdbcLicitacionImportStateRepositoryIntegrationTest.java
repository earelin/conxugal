package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionImportMode;
import gal.conxugal.domain.licitacion.LicitacionImportState;
import gal.conxugal.domain.licitacion.LicitacionImportStateRepository;
import gal.conxugal.domain.licitacion.LicitacionImportStatus;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The licitacións import state's adapter against a real PostgreSQL.
 *
 * <p>Not transactional, because the two writes take a transaction of their own: inside the test's
 * own they would not see the row it had not yet committed, which is neither what a walk does nor
 * what the propagation is for.
 *
 * <p>The last two claims are the ones this whole second table exists for: an Órgano's progress in
 * one contract family is never read as, nor disturbed by, its progress in the other.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionImportStateRepositoryIntegrationTest implements TestPropertyProvider {

  private static final Instant COVERED_THROUGH = Instant.parse("2026-08-06T09:00:00Z");
  private static final LocalDate CURSOR_DATE = LocalDate.of(2026, 7, 14);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionImportStateRepository importStates;

  @Inject
  ContratosMenoresImportStateRepository contratosMenoresImportStates;

  @Inject
  DataSource dataSource;

  @Inject
  DataSourceResolver dataSources;

  // The injected DataSource is the connection-context-aware proxy, which has no connection to give
  // outside a transaction — and this class deliberately runs outside one, because the writes under
  // test take transactions of their own.
  private DataSource unproxied;
  private SchemaFixture schema;

  @BeforeEach
  void setUp() {
    unproxied = dataSources.resolve(dataSource);
    schema = SchemaFixture.committing(unproxied);
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(unproxied);
  }

  // No row is the fact, not a missing one: it is what puts an Órgano marked long ago into the
  // initial mode without a migration and without being re-marked.
  @Test
  void an_organo_whose_licitacions_were_never_loaded_has_no_state_stored() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    Optional<LicitacionImportState> stored = importStates.findByOrganoId(organoId);

    assertThat(stored).isEmpty();
    assertThat(LicitacionImportMode.of(LicitacionImportStatus.NEVER_STARTED))
        .isEqualTo(LicitacionImportMode.INITIAL);
  }

  @Test
  void stores_started_import_as_incomplete_with_no_page_consumed() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    importStates.insert(LicitacionImportState.started(organoId));

    assertThat(importStateTable())
        .row(0)
        .value("organo_id")
        .isEqualTo(organoId.value())
        .value("state")
        .isEqualTo("INCOMPLETE")
        .value("cursor_offset")
        .isNull();
  }

  @Test
  void reads_back_the_stored_state_whole() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    importStates.insert(LicitacionImportState.started(organoId));
    importStates.updateCursorOffset(organoId, 400);

    LicitacionImportState stored = importStates.findByOrganoId(organoId).orElseThrow();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(stored.organoId()).isEqualTo(organoId);
      softly.assertThat(stored.state()).isEqualTo(LicitacionImportStatus.INCOMPLETE);
      softly.assertThat(stored.cursorOffset()).isEqualTo(400);
      softly.assertThat(stored.mode()).isEqualTo(LicitacionImportMode.RESUMED);
    });
  }

  // A cursor write cannot turn a resumption into a completion, which is what would let an Órgano
  // whose walk was interrupted be treated as loaded.
  @Test
  void advancing_the_cursor_twice_never_moves_the_status() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    importStates.insert(LicitacionImportState.started(organoId));

    importStates.updateCursorOffset(organoId, 100);
    importStates.updateCursorOffset(organoId, 200);

    assertThat(importStateTable())
        .row(0)
        .value("state")
        .isEqualTo("INCOMPLETE")
        .value("cursor_offset")
        .isEqualTo(200);
  }

  @Test
  void completing_an_import_leaves_the_cursor_where_it_stood() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    importStates.insert(LicitacionImportState.started(organoId));
    importStates.updateCursorOffset(organoId, 16_800);

    importStates.updateState(organoId, LicitacionImportStatus.COMPLETE);

    assertThat(importStateTable())
        .row(0)
        .value("state")
        .isEqualTo("COMPLETE")
        .value("cursor_offset")
        .isEqualTo(16_800);
  }

  // Both writes, and both rows: an update that forgot its WHERE clause moves every Órgano at once,
  // and a suite exercising one write or reading one row would not see it. Ordered by the status,
  // which the act itself makes distinct, so neither row's position rests on how uuidv7 sorted.
  @Test
  void advancing_one_organo_leaves_every_other_organo_untouched() throws SQLException {
    OrganoId sergas = schema.organo("sergas");
    OrganoId augas = schema.organo("augas-de-galicia");
    importStates.insert(LicitacionImportState.started(sergas));
    importStates.insert(LicitacionImportState.started(augas));

    importStates.updateCursorOffset(sergas, 300);
    importStates.updateState(sergas, LicitacionImportStatus.COMPLETE);

    assertThat(importStateByStatus())
        .hasNumberOfRows(2)
        .row(0)
        .value("organo_id")
        .isEqualTo(sergas.value())
        .value("state")
        .isEqualTo("COMPLETE")
        .value("cursor_offset")
        .isEqualTo(300)
        .row(1)
        .value("organo_id")
        .isEqualTo(augas.value())
        .value("state")
        .isEqualTo("INCOMPLETE")
        .value("cursor_offset")
        .isNull();
  }

  // Two families, two tables, and the whole point of the second one: the other family's row is
  // read as PostgreSQL renders a whole row, so the claim is about every one of its bytes.
  @Test
  void writing_the_licitacions_state_leaves_the_other_family_untouched() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    contratosMenoresImportStates.insert(
        ContratosMenoresImportState.startedAt(organoId, COVERED_THROUGH));
    contratosMenoresImportStates.updateCursorDate(organoId, CURSOR_DATE);
    contratosMenoresImportStates.updateRefreshedThrough(organoId, COVERED_THROUGH);
    final String before = schema.contratosMenoresImportStateRowOf(organoId.value());

    importStates.insert(LicitacionImportState.started(organoId));
    importStates.updateCursorOffset(organoId, 400);
    importStates.updateState(organoId, LicitacionImportStatus.COMPLETE);

    assertThat(schema.contratosMenoresImportStateRowOf(organoId.value())).isEqualTo(before);
  }

  @Test
  void completing_the_other_family_leaves_the_licitacions_state_untouched() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    importStates.insert(LicitacionImportState.started(organoId));
    importStates.updateCursorOffset(organoId, 400);
    contratosMenoresImportStates.insert(
        ContratosMenoresImportState.startedAt(organoId, COVERED_THROUGH));
    final String before = schema.licitacionImportStateRowOf(organoId.value());

    contratosMenoresImportStates.updateCursorDate(organoId, CURSOR_DATE);
    contratosMenoresImportStates.updateState(organoId, ContratosMenoresImportStatus.COMPLETE);

    assertThat(schema.licitacionImportStateRowOf(organoId.value())).isEqualTo(before);
  }

  private Table importStateTable() {
    return Tables.orderedBy(unproxied, "licitacion_import_state", "organo_id");
  }

  /** Ordered by the status, for the one test whose two Órganos end at different ones. */
  private Table importStateByStatus() {
    return Tables.orderedBy(unproxied, "licitacion_import_state", "state");
  }
}
