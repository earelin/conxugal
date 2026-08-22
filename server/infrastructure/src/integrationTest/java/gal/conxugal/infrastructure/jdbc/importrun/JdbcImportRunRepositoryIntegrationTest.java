package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.importrun.ContractFamily;
import gal.conxugal.domain.importrun.CoveredOrgano;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The guard, the derived-abandoned read and the run record, single-threaded. The race two claims
 * run is {@link ImportRunClaimConcurrencyIntegrationTest}'s.
 *
 * <p><strong>{@code transactional = false}</strong>, because every method of this adapter opens a
 * transaction of its own and an importer calls it with none in scope. The default would wrap each
 * test in a transaction that quietly supplied the connection each statement needs and joined the
 * writes into one — so the suite would pass for a reason production does not have, and a claim
 * that could only work inside somebody else's transaction would look correct. Fixtures and
 * assertions therefore go through raw connections off the container: what the adapter writes is
 * really committed.
 *
 * <p>Time is moved rather than back-dated: the clock the adapter reads is a mutable reference here,
 * so a run becomes stale because the world moved on, not because a test rewrote its row. That is
 * what makes "the stale row is untouched" provable rather than circular.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcImportRunRepositoryIntegrationTest implements TestPropertyProvider {

  private static final Instant CLAIMED_AT = Instant.parse("2026-08-07T09:00:00Z");
  private static final Duration BOUND = Duration.ofMinutes(15);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private final AtomicReference<Instant> now = new AtomicReference<>(CLAIMED_AT);

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @MockBean(Clock.class)
  Clock clock() {
    return now::get;
  }

  @Inject
  ImportRunRepository importRunRepository;

  @BeforeEach
  void resetTheClock() {
    now.set(CLAIMED_AT);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  @Test
  void claim_records_the_run_and_every_organo_it_covers_at_pending() throws Exception {
    OrganoId first = insertOrgano("consorcio-x");
    OrganoId second = insertOrgano("axencia-y");

    importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(first, second));

    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("importer").isEqualTo("CONTRATOS_MENORES");
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
    assertThat(runs).row(0).value("added").isEqualTo(0);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);

    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(1).value("state").isEqualTo("PENDING");
  }

  // Readable before any of them has been touched: a run that dies at the fortieth of four hundred
  // still has to be able to name the list it was going to cover.
  @Test
  void the_covered_organos_are_readable_straight_after_the_claim() throws Exception {
    OrganoId first = insertOrgano("consorcio-x");
    OrganoId second = insertOrgano("axencia-y");

    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(first, second))
            .orElseThrow();

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    assertThat(report.coveredOrganos())
        .extracting(ImportRunOrganoCoverage::organoId, ImportRunOrganoCoverage::state)
        .containsExactlyInAnyOrder(
            tuple(first, ImportRunOrganoState.PENDING),
            tuple(second, ImportRunOrganoState.PENDING));
  }

  // The catalogue import's shape: it covers no Órgano, and it still holds the guard.
  @Test
  void claim_covering_no_organos_records_run_that_covers_none() {
    ImportRunId runId = importRunRepository.claim(Importer.ORGANOS, List.of()).orElseThrow();

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    assertThat(report.coveredOrganos()).isEmpty();
    assertThat(report.importer()).isEqualTo(Importer.ORGANOS);
  }

  // Naming one twice is a caller's slip, not a run that covers it twice — and the composite key
  // would answer it by rolling the whole claim back, losing the guard with it.
  @Test
  void claim_naming_the_same_organo_twice_covers_it_once() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId, organoId))
            .orElseThrow();

    assertThat(importRunRepository.findRun(runId).orElseThrow().coveredOrganos()).hasSize(1);
  }

  // What a mark asking for both families claims: one run, one guard taken once, and two rows for
  // the one Órgano — which is exactly what the shipped two-column key forbade.
  @Test
  void claim_covering_one_organo_for_both_families_records_one_row_per_family() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    bothFamiliesOf(organoId);

    // Asserted against the table rather than through findRun, which filters by run: a binding that
    // also wrote a row against some other run would read back correctly here and still be wrong.
    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).row(0).value("organo_id").isEqualTo(organoId.value());
    assertThat(coverage).row(0).value("family").isEqualTo("CONTRATOS_MENORES");
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(1).value("organo_id").isEqualTo(organoId.value());
    assertThat(coverage).row(1).value("family").isEqualTo("LICITACIONS");
    assertThat(coverage).row(1).value("state").isEqualTo("PENDING");
  }

  // The two rows fare separately, which is the whole point of the re-key: one family failing has
  // to be reportable beside the other's counts rather than overwriting them.
  @Test
  void each_family_of_one_organo_carries_its_own_state_counts_and_reason() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId = bothFamiliesOf(organoId);

    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 4);
    importRunRepository.finishOrgano(
        runId, organoId, ContractFamily.CONTRATOS_MENORES, ImportRunOrganoState.SUCCEEDED, null);
    importRunRepository.finishOrgano(
        runId,
        organoId,
        ContractFamily.LICITACIONS,
        ImportRunOrganoState.FAILED,
        "the record would not parse");

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(coverageFor(report, organoId, ContractFamily.CONTRATOS_MENORES))
          .isEqualTo(contratosMenoresCoverage(
              organoId, ImportRunOrganoState.SUCCEEDED, 100, 4, null));
      softly.assertThat(coverageFor(report, organoId, ContractFamily.LICITACIONS))
          .isEqualTo(licitacionsCoverage(
              organoId, ImportRunOrganoState.FAILED, 0, 0, "the record would not parse"));
    });
  }

  // Addressing a coverage row by (run, Órgano) alone would now update whichever of the two the
  // database returned first, silently and differently on different runs.
  @Test
  void advancing_one_family_leaves_the_other_familys_row_untouched() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId = bothFamiliesOf(organoId);

    importRunRepository.advance(runId, organoId, ContractFamily.LICITACIONS, 42, 7);

    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).row(0).value("family").isEqualTo("CONTRATOS_MENORES");
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(0).value("added").isEqualTo(0);
    assertThat(coverage).row(0).value("refreshed").isEqualTo(0);
    assertThat(coverage).row(1).value("family").isEqualTo("LICITACIONS");
    assertThat(coverage).row(1).value("state").isEqualTo("IN_PROGRESS");
    assertThat(coverage).row(1).value("added").isEqualTo(42);
    assertThat(coverage).row(1).value("refreshed").isEqualTo(7);
  }

  @Test
  void settling_one_family_leaves_the_other_familys_row_untouched() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId = bothFamiliesOf(organoId);

    importRunRepository.finishOrgano(
        runId,
        organoId,
        ContractFamily.LICITACIONS,
        ImportRunOrganoState.FAILED,
        "the record would not parse");

    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).row(0).value("family").isEqualTo("CONTRATOS_MENORES");
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(0).value("failure_reason").isNull();
    assertThat(coverage).row(1).value("family").isEqualTo("LICITACIONS");
    assertThat(coverage).row(1).value("state").isEqualTo("FAILED");
    assertThat(coverage).row(1).value("failure_reason").isEqualTo("the record would not parse");
  }

  @Test
  void second_claim_of_the_same_importer_while_run_advances_is_refused() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId));

    Optional<ImportRunId> refused =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId));

    assertThat(refused).isEmpty();
  }

  // The guard is system-wide, not one per importer: the catalogue import is held by a contratos
  // menores run and the other way round.
  @Test
  void claim_of_the_other_importer_while_run_advances_is_refused() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isEmpty();
  }

  @Test
  void refused_claim_inserts_nothing() throws Exception {
    OrganoId held = insertOrgano("consorcio-x");
    OrganoId other = insertOrgano("axencia-y");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(held));

    // Coverage on the refused claim is the point — it is what proves the enumeration was skipped
    // too, not only the run row. A licitacións trigger rather than the catalogue import, which
    // covers no Órgano at all and so could not make that claim.
    importRunRepository.claim(
        Importer.LICITACIONS, List.of(new CoveredOrgano(other, ContractFamily.LICITACIONS)));

    assertThat(runTable()).hasNumberOfRows(1);
    assertThat(coverageTable()).hasNumberOfRows(1);
  }

  @Test
  void run_advanced_within_the_bound_still_holds_the_guard() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).minusSeconds(60));
    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 0);
    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(60));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isEmpty();
  }

  // Without this rule one crash mid-run would hold every import in the system for good: nothing
  // sweeps the row and no administrative function clears it.
  @Test
  void run_that_has_not_advanced_within_the_bound_releases_the_guard() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId));

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isPresent();
  }

  @Test
  void stale_runs_row_and_its_coverage_are_left_exactly_as_they_stood() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    final ImportRunId stale =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));
    importRunRepository.claim(Importer.ORGANOS, List.of());

    // Ordered by start, so row 0 is the run that died and row 1 the one that took the guard from
    // it — both asserted, so an update that reached the wrong row could not pass.
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(2);
    assertThat(runs).row(1).value("importer").isEqualTo("ORGANOS");
    assertThat(runs).row(0).value("importer").isEqualTo("CONTRATOS_MENORES");
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
    assertThat(runs).row(0).value("added").isEqualTo(0);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);
    assertThat(storedStartedAt(stale)).isEqualTo(CLAIMED_AT);
    assertThat(storedLastAdvancedAt(stale)).isEqualTo(CLAIMED_AT);

    // The coverage row is what a mis-scoped update from the second claim would reach, and the
    // second claim covers no Órgano at all, so it must still read exactly as it was enumerated.
    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(1);
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(0).value("added").isEqualTo(0);
    assertThat(coverage).row(0).value("refreshed").isEqualTo(0);
    assertThat(coverage).row(0).value("failure_reason").isNull();
  }

  @Test
  void every_read_of_stale_run_reports_it_abandoned() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId stale =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));

    assertThat(importRunRepository.findRun(stale).orElseThrow().state())
        .isEqualTo(ImportRunState.ABANDONED);
  }

  @Test
  void advance_moves_the_runs_counts_and_the_covered_organos_own() throws Exception {
    OrganoId advanced = insertOrgano("consorcio-x");
    OrganoId untouched = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(advanced, untouched))
            .orElseThrow();

    importRunRepository.advance(runId, advanced, ContractFamily.CONTRATOS_MENORES, 100, 4);
    importRunRepository.advance(runId, advanced, ContractFamily.CONTRATOS_MENORES, 60, 6);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isEqualTo(160);
      softly.assertThat(report.refreshed()).isEqualTo(10);
      softly.assertThat(coverageFor(report, advanced))
          .isEqualTo(contratosMenoresCoverage(
              advanced, ImportRunOrganoState.IN_PROGRESS, 160, 10, null));
      softly.assertThat(coverageFor(report, untouched))
          .isEqualTo(contratosMenoresCoverage(
              untouched, ImportRunOrganoState.PENDING, 0, 0, null));
    });
  }

  // The run's counts are the sum of its coverage rows', so a batch no covered Órgano accepted must
  // not reach them — otherwise the outcome reports contracts against nothing that imported them.
  @Test
  void advance_against_organo_the_run_does_not_cover_counts_nothing() throws Exception {
    OrganoId covered = insertOrgano("consorcio-x");
    OrganoId uncovered = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(covered))
            .orElseThrow();

    importRunRepository.advance(runId, uncovered, ContractFamily.CONTRATOS_MENORES, 100, 4);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isZero();
      softly.assertThat(report.refreshed()).isZero();
      softly.assertThat(report.coveredOrganos())
          .containsExactly(contratosMenoresCoverage(
              covered, ImportRunOrganoState.PENDING, 0, 0, null));
    });
  }

  // A late batch must not un-fail an Órgano the outcome has to report as failed, nor leave it in
  // progress while its reason stays behind.
  @Test
  void advance_after_the_organo_has_been_settled_leaves_it_settled() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();
    importRunRepository.finishOrgano(
        runId,
        organoId,
        ContractFamily.CONTRATOS_MENORES,
        ImportRunOrganoState.FAILED, "the source stopped answering");

    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 4);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isZero();
      softly.assertThat(coverageFor(report, organoId))
          .isEqualTo(contratosMenoresCoverage(
              organoId, ImportRunOrganoState.FAILED, 0, 0, "the source stopped answering"));
    });
  }

  @Test
  void finishing_an_organo_records_its_state_and_its_reason() throws Exception {
    OrganoId failed = insertOrgano("consorcio-x");
    OrganoId stopped = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(failed, stopped))
            .orElseThrow();

    importRunRepository.finishOrgano(
        runId,
        failed,
        ContractFamily.CONTRATOS_MENORES,
        ImportRunOrganoState.FAILED, "the source stopped answering");
    importRunRepository.finishOrgano(
        runId, stopped, ContractFamily.CONTRATOS_MENORES, ImportRunOrganoState.STOPPED, null);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(coverageFor(report, failed))
          .isEqualTo(contratosMenoresCoverage(
              failed, ImportRunOrganoState.FAILED, 0, 0, "the source stopped answering"));
      softly.assertThat(coverageFor(report, stopped))
          .isEqualTo(contratosMenoresCoverage(
              stopped, ImportRunOrganoState.STOPPED, 0, 0, null));
    });
  }

  // Nothing settles this state any more, but runs recorded before the incremental refresh existed
  // carry it. Reading one back is what keeps the value's removal off the table: an enum value gone
  // is a stored row that no longer deserialises at all.
  @Test
  void organo_settled_as_skipped_before_the_refresh_existed_still_reads_back() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();

    importRunRepository.finishOrgano(
        runId,
        organoId,
        ContractFamily.CONTRATOS_MENORES,
        ImportRunOrganoState.SKIPPED, "its history was already loaded");

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    assertThat(coverageFor(report, organoId))
        .isEqualTo(contratosMenoresCoverage(
            organoId, ImportRunOrganoState.SKIPPED, 0, 0, "its history was already loaded"));
  }

  @Test
  void settling_organo_the_run_does_not_cover_records_nothing() throws Exception {
    OrganoId covered = insertOrgano("consorcio-x");
    OrganoId uncovered = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(covered))
            .orElseThrow();

    importRunRepository.finishOrgano(
        runId, uncovered, ContractFamily.CONTRATOS_MENORES, ImportRunOrganoState.FAILED, "nowhere");

    assertThat(coverageTable()).hasNumberOfRows(1);
    assertThat(importRunRepository.findRun(runId).orElseThrow().coveredOrganos())
        .containsExactly(
            contratosMenoresCoverage(covered, ImportRunOrganoState.PENDING, 0, 0, null));
  }

  @Test
  void completing_run_records_its_verdict_its_finish_time_and_its_counts() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();
    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 4);
    importRunRepository.finishOrgano(
        runId, organoId, ContractFamily.CONTRATOS_MENORES, ImportRunOrganoState.SUCCEEDED, null);

    Instant finishedAt = CLAIMED_AT.plusSeconds(600);
    now.set(finishedAt);
    importRunRepository.complete(runId, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.state()).isEqualTo(ImportRunState.PARTIALLY_SUCCEEDED);
      softly.assertThat(report.finishedAt()).isEqualTo(finishedAt);
      softly.assertThat(report.startedAt()).isEqualTo(CLAIMED_AT);
      softly.assertThat(report.added()).isEqualTo(100);
      softly.assertThat(report.refreshed()).isEqualTo(4);
    });
  }

  // The shape of an importer whose whole run is one act: it never advances, so completion is its
  // only chance to say what it counted. The counts add rather than replace, on the same rule an
  // advance follows, and the finish stamps the last advance so the two timestamps agree.
  @Test
  void completing_run_adds_the_counts_it_carries_and_stamps_the_last_advance() throws Exception {
    ImportRunId runId = importRunRepository.claim(Importer.ORGANOS, List.of()).orElseThrow();

    Instant finishedAt = CLAIMED_AT.plusSeconds(90);
    now.set(finishedAt);
    importRunRepository.complete(runId, ImportRunState.SUCCEEDED, 12, 340);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isEqualTo(12);
      softly.assertThat(report.refreshed()).isEqualTo(340);
      softly.assertThat(report.coveredOrganos()).isEmpty();
    });
    assertThat(storedLastAdvancedAt(runId)).isEqualTo(finishedAt);
  }

  // A run cannot be completed as abandoned: that state is derived on read and stored nowhere, and
  // a caller completing with a state it just read has one in hand.
  @Test
  void completing_run_as_abandoned_is_refused_and_writes_nothing() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> importRunRepository.complete(runId, ImportRunState.ABANDONED, 0, 0));

    Table runs = runTable();
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
  }

  // The shape of an importer that reports as it goes and therefore settles with zeroes: what an
  // advance already counted stays counted once. An importer passing its run totals here instead
  // would count everything twice, which is why the completion parameters are named for the
  // remainder rather than for the total.
  @Test
  void completing_run_that_advanced_adds_to_what_the_advance_counted() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();
    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 4);
    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 30, 1);

    importRunRepository.complete(runId, ImportRunState.SUCCEEDED, 0, 0);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isEqualTo(130);
      softly.assertThat(report.refreshed()).isEqualTo(5);
    });
  }

  // Re-completing is a caller's mistake with no requirement attached. The verdict it is given is
  // the one that stands, and the coverage rows are none of its business — but the counts it
  // carries are added again, because the statement cannot tell a retry from a second remainder.
  @Test
  void completing_run_that_is_already_complete_takes_the_new_verdict_and_adds_its_counts()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();
    importRunRepository.advance(runId, organoId, ContractFamily.CONTRATOS_MENORES, 100, 4);
    importRunRepository.finishOrgano(
        runId, organoId, ContractFamily.CONTRATOS_MENORES, ImportRunOrganoState.SUCCEEDED, null);
    importRunRepository.complete(runId, ImportRunState.SUCCEEDED, 0, 0);

    importRunRepository.complete(runId, ImportRunState.FAILED, 7, 0);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.state()).isEqualTo(ImportRunState.FAILED);
      softly.assertThat(report.added()).isEqualTo(107);
      softly.assertThat(coverageFor(report, organoId).state())
          .isEqualTo(ImportRunOrganoState.SUCCEEDED);
    });
  }

  @Test
  void completing_unknown_run_records_nothing() {
    importRunRepository.complete(
        new ImportRunId(UUID.randomUUID()), ImportRunState.SUCCEEDED, 0, 0);

    assertThat(runTable()).hasNumberOfRows(0);
  }

  // Every verdict releases the guard for good — a finished run cannot become abandoned, so the
  // next claim must not have to wait out the bound first.
  @Test
  void completed_run_holds_the_guard_no_longer_whatever_its_verdict() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId succeeded =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, contratosMenores(organoId))
            .orElseThrow();
    importRunRepository.complete(succeeded, ImportRunState.SUCCEEDED, 0, 0);

    ImportRunId failed = importRunRepository.claim(Importer.ORGANOS, List.of()).orElseThrow();
    importRunRepository.complete(failed, ImportRunState.FAILED, 0, 0);

    ImportRunId partial =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of()).orElseThrow();
    importRunRepository.complete(partial, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isPresent();
  }

  @Test
  void read_of_an_unknown_run_is_empty() {
    assertThat(importRunRepository.findRun(new ImportRunId(UUID.randomUUID()))).isEmpty();
  }

  /** One run covering one Órgano for both families, in the order a mark asks for them. */
  private ImportRunId bothFamiliesOf(OrganoId organoId) {
    return importRunRepository
        .claim(
            Importer.AMBAS_FAMILIAS,
            List.of(
                new CoveredOrgano(organoId, ContractFamily.CONTRATOS_MENORES),
                new CoveredOrgano(organoId, ContractFamily.LICITACIONS)))
        .orElseThrow();
  }

  /** The shipped family's coverage, which is what all but the two-family tests above claim. */
  private static List<CoveredOrgano> contratosMenores(OrganoId... organoIds) {
    return Arrays.stream(organoIds)
        .map(organoId -> new CoveredOrgano(organoId, ContractFamily.CONTRATOS_MENORES))
        .toList();
  }

  private static ImportRunOrganoCoverage contratosMenoresCoverage(
      OrganoId organoId,
      ImportRunOrganoState state,
      int added,
      int refreshed,
      @Nullable String failureReason) {
    return coverage(
        organoId, ContractFamily.CONTRATOS_MENORES, state, added, refreshed, failureReason);
  }

  private static ImportRunOrganoCoverage licitacionsCoverage(
      OrganoId organoId,
      ImportRunOrganoState state,
      int added,
      int refreshed,
      @Nullable String failureReason) {
    return coverage(organoId, ContractFamily.LICITACIONS, state, added, refreshed, failureReason);
  }

  private static ImportRunOrganoCoverage coverage(
      OrganoId organoId,
      ContractFamily family,
      ImportRunOrganoState state,
      int added,
      int refreshed,
      @Nullable String failureReason) {
    return new ImportRunOrganoCoverage(organoId, family, state, added, refreshed, failureReason);
  }

  private static ImportRunOrganoCoverage coverageFor(ImportRunReport report, OrganoId organoId) {
    return coverageFor(report, organoId, ContractFamily.CONTRATOS_MENORES);
  }

  private static ImportRunOrganoCoverage coverageFor(
      ImportRunReport report, OrganoId organoId, ContractFamily family) {
    return report.coveredOrganos().stream()
        .filter(coverage -> coverage.organoId().equals(organoId) && coverage.family() == family)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "the %s of Órgano %s is not covered".formatted(family, organoId)));
  }

  private Table runTable() {
    return assertDb()
        .table("import_run")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("started_at")})
        .build();
  }

  private Table coverageTable() {
    return assertDb()
        .table("import_run_organo")
        .columnsToOrder(
            new Table.Order[] {Table.Order.asc("organo_id"), Table.Order.asc("family")})
        .build();
  }

  // Off the container rather than the injected DataSource: with no ambient transaction the adapter
  // really commits, and these assertions are about what it committed.
  private static AssertDbConnection assertDb() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create();
  }

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a timestamptz
  // against the session time zone and the claims here are about the instant.
  private Instant storedStartedAt(ImportRunId runId) throws SQLException {
    return storedInstant(runId, "SELECT started_at FROM import_run WHERE id = ?", "started_at");
  }

  private Instant storedLastAdvancedAt(ImportRunId runId) throws SQLException {
    return storedInstant(
        runId, "SELECT last_advanced_at FROM import_run WHERE id = ?", "last_advanced_at");
  }

  private Instant storedInstant(ImportRunId runId, String sql, String column) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, runId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No run stored under %s".formatted(runId));
        }
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
      }
    }
  }

  private static OrganoId insertOrgano(String sourceKey) throws SQLException {
    try (Connection connection = rawConnection()) {
      return OrganoFixture.insert(connection, sourceKey);
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
