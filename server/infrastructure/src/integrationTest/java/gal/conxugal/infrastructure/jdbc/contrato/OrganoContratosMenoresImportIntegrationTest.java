package gal.conxugal.infrastructure.jdbc.contrato;

import static gal.conxugal.infrastructure.jdbc.contrato.ContratosMenoresImportFixture.SOURCE_KEY;
import static gal.conxugal.infrastructure.jdbc.contrato.ContratosMenoresImportFixture.organo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.contrato.ContratosMenoresImportSummary;
import gal.conxugal.domain.contrato.ImportOrganoContratosMenores;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One Órgano's walk against a real PostgreSQL and a stubbed source: what an interrupted import
 * retains, where a resumption picks up, and that re-reading changes nothing but the values a
 * publication itself changed.
 *
 * <p><strong>{@code transactional = false}</strong>, because the whole point is which writes
 * commit separately: contracts in one transaction, the cursor and the run's progress in their own.
 * The default would join them into one the test rolled back, and every claim here would hold for a
 * reason production does not have.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganoContratosMenoresImportIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-07T09:00:00Z");

  /** Inside every window these tests read, so which window it lands in is never the claim. */
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 6, 1);

  private static final LocalDate FIRST_WINDOW_START = LocalDate.of(2026, 5, 10);
  private static final LocalDate SECOND_WINDOW_START = LocalDate.of(2026, 2, 10);
  private static final LocalDate THIRD_WINDOW_START = LocalDate.of(2025, 11, 13);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private final ContratosMenoresImportFixture fixture = new ContratosMenoresImportFixture(postgres);
  private final Map<LocalDate, List<ContratoMenorSourceEntry>> published = new HashMap<>();
  private final List<LocalDate> readWindows = new ArrayList<>();
  private LocalDate unreachableWindow;
  private long recordsTotal;

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @MockBean(Clock.class)
  Clock clock() {
    return () -> T_ZERO;
  }

  @MockBean(ContratoMenorSource.class)
  ContratoMenorSource contratoMenorSourceMock() {
    return mock(ContratoMenorSource.class);
  }

  @Inject
  ImportOrganoContratosMenores importContratosMenores;

  @Inject
  ContratoMenorSource contratoMenorSource;

  @Inject
  ImportRunRepository importRuns;

  @Inject
  ContratosMenoresImportStateRepository importStates;

  @Inject
  TransactionOperations<Connection> transactions;

  @BeforeEach
  void publishThreeContractsAcrossThreeWindows() {
    published.clear();
    readWindows.clear();
    unreachableWindow = null;
    recordsTotal = 3;
    published.put(FIRST_WINDOW_START, List.of(entry(1, "Servizos eléctricos", "3630.00")));
    published.put(SECOND_WINDOW_START, List.of(entry(2, "Subministración de papel", "480.50")));
    published.put(THIRD_WINDOW_START, List.of(entry(3, "Mantemento de xardíns", "1200.00")));
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          if (from.equals(unreachableWindow)) {
            throw new ContratoMenorSourceUnavailableException("source is down");
          }
          readWindows.add(from);
          return new ContratoMenorSourcePage(
              published.getOrDefault(from, List.of()), recordsTotal);
        });
  }

  @AfterEach
  void cleanUp() throws Exception {
    fixture.truncateEveryTable();
  }

  @Test
  void resumption_continues_from_the_cursor_reading_fewer_windows_than_restarting()
      throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    interruptedWalkOf(organoId);
    readWindows.clear();

    ContratosMenoresImportSummary resumed = walk(organoId);

    assertThat(readWindows).containsExactly(THIRD_WINDOW_START);
    assertThat(resumed.status()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
    assertThat(fixture.contratoTable()).hasNumberOfRows(3);
    Table states = fixture.importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("state").isEqualTo("COMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(THIRD_WINDOW_START);
  }

  // Both halves of an advance commit outside the transaction that wrote the contracts, so what an
  // interrupted walk left behind is readable rather than rolled back with the run that failed.
  @Test
  void interrupted_walk_leaves_its_cursor_and_its_run_counts_committed() throws Exception {
    OrganoId organoId = fixture.insertOrgano();

    interruptedWalkOf(organoId);

    Table states = fixture.importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("organo_id").isEqualTo(organoId.value());
    assertThat(states).row(0).value("state").isEqualTo("INCOMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(SECOND_WINDOW_START);
    Table runs = fixture.runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("added").isEqualTo(2);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);
    Table coverage = fixture.coverageTable();
    assertThat(coverage).hasNumberOfRows(1);
    assertThat(coverage).row(0).value("organo_id").isEqualTo(organoId.value());
    assertThat(coverage).row(0).value("added").isEqualTo(2);
  }

  // The ordering the run record owes the import: a batch that committed has happened, and a
  // bookkeeping failure may not undo it. The record is what is sacrificed, never the contracts.
  @Test
  void contracts_stay_committed_when_the_run_cannot_be_advanced() throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    ImportRunId runId = claim(organoId);
    // Pruned from under the live walk: the advance finds no coverage row to move and gives up.
    fixture.execute("DELETE FROM import_run_organo");

    ContratosMenoresImportSummary summary =
        importContratosMenores.run(runId, organo(organoId), () -> true);

    assertThat(summary.status()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
    assertThat(fixture.contratoTable()).hasNumberOfRows(3);
    Table runs = fixture.runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("added").isEqualTo(0);
  }

  // The cursor is deliberately not part of whatever transaction its caller is inside: the walk
  // writes the batch first and the cursor after, and a data failure that undid the cursor would
  // leave the two halves of one advance disagreeing. Only the write's own propagation makes that
  // true from inside a caller's transaction, so it is asserted from inside one that rolls back.
  @Test
  void cursor_write_survives_the_rollback_of_the_transaction_it_was_called_in() throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    importStates.insert(ContratosMenoresImportState.startedAt(organoId, T_ZERO));

    transactions.executeWrite(status -> {
      importStates.updateCursorDate(organoId, FIRST_WINDOW_START);
      importStates.updateState(organoId, ContratosMenoresImportStatus.COMPLETE);
      status.setRollbackOnly();
      return null;
    });

    Table states = fixture.importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("cursor_date").isEqualTo(FIRST_WINDOW_START);
    assertThat(states).row(0).value("state").isEqualTo("COMPLETE");
  }

  // The cursor is written after the batch commits, so a crash in between leaves it behind what is
  // stored and the resumption reads that window again — deliberately, and safe because storing a
  // contract twice refreshes it in place.
  @Test
  void resumption_over_the_cursor_that_the_crash_left_behind_stores_nothing_twice()
      throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    interruptedWalkOf(organoId);
    final UUID identityBeforeTheOverlap = fixture.storedIdentityOf(2);
    rewindCursorTo(FIRST_WINDOW_START);
    readWindows.clear();

    walk(organoId);

    assertThat(readWindows).containsExactly(SECOND_WINDOW_START, THIRD_WINDOW_START);
    assertThat(fixture.contratoTable()).hasNumberOfRows(3);
    assertThat(fixture.storedIdentityOf(2)).isEqualTo(identityBeforeTheOverlap);
  }

  // Run history is pruned, and an Órgano interrupted mid-import has no successful run to protect
  // its rows. The cursor lives with the Órgano precisely so that costs it nothing.
  @Test
  void resumption_is_unaffected_by_every_run_record_being_pruned() throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    interruptedWalkOf(organoId);
    pruneEveryRunRecord();
    readWindows.clear();

    ContratosMenoresImportSummary resumed = walk(organoId);

    assertThat(readWindows).containsExactly(THIRD_WINDOW_START);
    assertThat(resumed.status()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
    assertThat(fixture.contratoTable()).hasNumberOfRows(3);
  }

  // One window, not three: completeness is tested against the count already stored, so a walk over
  // a history it already holds is finished as soon as it has read anything at all. What matters is
  // that the window it did read added nothing and changed nothing.
  @Test
  void walking_the_same_published_history_twice_adds_nothing_the_second_time() throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    walk(organoId);
    forgetHowFarTheImportGot();

    ContratosMenoresImportSummary second = walk(organoId);

    assertThat(second).isEqualTo(ContratosMenoresImportSummary.complete(0, 1));
    Table contratos = fixture.contratoTable();
    assertThat(contratos).hasNumberOfRows(3);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos");
    assertThat(contratos).row(1).value("obxecto").isEqualTo("Subministración de papel");
    assertThat(contratos).row(2).value("obxecto").isEqualTo("Mantemento de xardíns");
  }

  @Test
  void publication_whose_attributes_changed_at_the_source_is_refreshed_in_place() throws Exception {
    OrganoId organoId = fixture.insertOrgano();
    walk(organoId);
    final UUID identityBeforeTheChange = fixture.storedIdentityOf(1);
    published.put(
        FIRST_WINDOW_START, List.of(entry(1, "Servizos eléctricos, corrixido", "4000.00")));
    forgetHowFarTheImportGot();

    walk(organoId);

    Table contratos = fixture.contratoTable();
    assertThat(contratos).hasNumberOfRows(3);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos, corrixido");
    assertThat(contratos).row(0).value("amount").isEqualTo(new BigDecimal("4000.00"));
    assertThat(fixture.storedIdentityOf(1)).isEqualTo(identityBeforeTheChange);
  }

  /**
   * A walk that read two of the three windows and then lost the source, settled as the orchestrator
   * would settle it so the guard is free for the resumption to claim.
   */
  private void interruptedWalkOf(OrganoId organoId) {
    unreachableWindow = THIRD_WINDOW_START;
    ImportRunId runId = claim(organoId);
    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(() -> importContratosMenores.run(runId, organo(organoId), () -> true));
    importRuns.complete(runId, ImportRunState.FAILED, 0, 0);
    assertThat(fixture.contratoTable()).hasNumberOfRows(2);
    unreachableWindow = null;
  }

  private ContratosMenoresImportSummary walk(OrganoId organoId) {
    ImportRunId runId = claim(organoId);
    ContratosMenoresImportSummary summary =
        importContratosMenores.run(runId, organo(organoId), () -> true);
    importRuns.complete(runId, ImportRunState.SUCCEEDED, 0, 0);
    return summary;
  }

  private ImportRunId claim(OrganoId organoId) {
    return importRuns
        .claim(Importer.CONTRATOS_MENORES, List.of(organoId))
        .orElseThrow(() -> new IllegalStateException("the import guard was already held"));
  }

  private static ContratoMenorSourceEntry entry(long sourceId, String obxecto, String amount) {
    return ContratosMenoresImportFixture.entry(sourceId, PUBLISHED_ON, obxecto, amount);
  }

  /** The cursor as a crash between a batch's commit and its cursor write would have left it. */
  private void rewindCursorTo(LocalDate cursorDate) throws SQLException {
    fixture.execute("UPDATE contrato_menor_import_state SET cursor_date = ?", cursorDate);
  }

  private void pruneEveryRunRecord() throws SQLException {
    fixture.execute("DELETE FROM import_run_organo");
    fixture.execute("DELETE FROM import_run");
  }

  /**
   * Drops the Órgano's state row, so the next walk is an initial import over a history it already
   * holds — which is what "the same import run twice" means once the first one completed.
   */
  private void forgetHowFarTheImportGot() throws SQLException {
    fixture.execute("DELETE FROM contrato_menor_import_state");
  }
}
