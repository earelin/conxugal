package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ContractFamily;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Walking a claimed run: the order its Órganos are taken in, and what the whole thing amounts to.
 * What one Órgano's turn means is {@link ImportCoveredOrgano}'s and is stubbed here.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteContratosMenoresImportTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId FIRST = new OrganoId(UUID.randomUUID());
  private static final OrganoId SECOND = new OrganoId(UUID.randomUUID());
  private static final OrganoId THIRD = new OrganoId(UUID.randomUUID());
  private static final OrganoId FOURTH = new OrganoId(UUID.randomUUID());
  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private ImportCoveredOrgano coveredOrgano;

  @Mock
  private OrganoRepository organos;

  private final List<OrganoId> imported = new ArrayList<>();

  // Serial, and within a mode in the order the run recorded: a run is working on one identifiable
  // Órgano at any moment, which is what makes its per-Órgano outcomes reportable.
  @Test
  void imports_the_covered_organos_one_after_another_in_the_order_the_run_holds_them() {
    runCovers(FIRST, SECOND, THIRD);
    catalogueHolds(FIRST, SECOND, THIRD);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST, SECOND, THIRD);
  }

  // A run asked for both families holds two rows per Órgano, and this walks one of them. Taking
  // every row would walk each Órgano twice and settle the licitacións row on this walk's outcome.
  @Test
  void walks_only_the_contratos_menores_rows_when_the_run_covers_both_families() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    runIsRecordedWithCoverage(
        List.of(
            pending(FIRST, ContractFamily.CONTRATOS_MENORES),
            pending(FIRST, ContractFamily.LICITACIONS),
            pending(SECOND, ContractFamily.CONTRATOS_MENORES),
            pending(SECOND, ContractFamily.LICITACIONS)));
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST, SECOND);
  }

  @Test
  void carries_on_to_the_next_organo_when_one_of_them_failed() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(
        organoId ->
            organoId.equals(FIRST)
                ? ImportRunOrganoState.FAILED
                : ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST, SECOND);
  }

  @Test
  void reads_no_organo_when_the_run_it_was_handed_is_no_longer_the_live_one() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(false);

    executeContratosMenoresImport().execute(RUN_ID);

    verifyNoInteractions(coveredOrgano);
    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // Defence rather than a reachable state — the guard and the coverage are read off the same row —
  // but a run that answers no coverage must walk nothing rather than fall through to the catalogue.
  @Test
  void reads_no_organo_when_the_run_it_was_handed_is_not_recorded() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importRuns.findRun(RUN_ID)).thenReturn(Optional.empty());

    executeContratosMenoresImport().execute(RUN_ID);

    verifyNoInteractions(coveredOrgano);
  }

  // --------------------------------------------------------- the sweep's order

  // R22's delegated prioritisation: the cheap work first, so a catalogue's freshness is never
  // hostage to the position of one row.
  @Test
  void walks_incremental_then_resumed_then_initial_whatever_order_the_run_recorded() {
    runCovers(FIRST, SECOND, THIRD);
    catalogueHolds(FIRST, ContratosMenoresImportStatus.NEVER_STARTED);
    catalogueHolds(SECOND, ContratosMenoresImportStatus.INCOMPLETE);
    catalogueHolds(THIRD, ContratosMenoresImportStatus.COMPLETE);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(THIRD, SECOND, FIRST);
  }

  // A newly marked Órgano's multi-day load runs after the refreshes, and both are still covered by
  // the one run — the exhaustive assertion is the membership proof.
  @Test
  void walks_the_newly_marked_organo_after_every_loaded_one() {
    runCovers(FIRST, SECOND, THIRD);
    catalogueHolds(FIRST, ContratosMenoresImportStatus.NEVER_STARTED);
    catalogueHolds(SECOND, THIRD);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(SECOND, THIRD, FIRST);
  }

  // Gone from the catalogue costs nothing to settle, so it goes first — and it is still walked:
  // the ordering read never drops an Órgano from the coverage. What its turn amounts to stays
  // ImportCoveredOrgano's and is stubbed here as the failure that class settles it with.
  @Test
  void walks_the_organo_gone_from_the_catalogue_before_any_other() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST);
    goneFromTheCatalogue(SECOND);
    eachOrganoSettles(
        organoId ->
            organoId.equals(SECOND)
                ? ImportRunOrganoState.FAILED
                : ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(SECOND, FIRST);
  }

  // Interleaved modes rather than uniform ones, so keeping the recorded order proves the sort is
  // stable and not merely order-preserving on input it never had to move.
  @Test
  void keeps_the_recorded_order_between_organos_sharing_the_same_mode() {
    runCovers(FIRST, SECOND, THIRD, FOURTH);
    catalogueHolds(FIRST, THIRD);
    catalogueHolds(SECOND, ContratosMenoresImportStatus.NEVER_STARTED);
    catalogueHolds(FOURTH, ContratosMenoresImportStatus.NEVER_STARTED);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST, THIRD, SECOND, FOURTH);
  }

  // A momentary failure to read one row while ordering must not abandon the sweep: that Órgano is
  // taken last — after even an initial load — and its own turn re-reads the catalogue and settles
  // it properly.
  @Test
  void walks_every_covered_organo_when_the_ordering_read_of_one_of_them_throws() {
    runCovers(FIRST, SECOND);
    when(organos.findById(FIRST)).thenThrow(new IllegalStateException("the row is unreadable"));
    catalogueHolds(SECOND, ContratosMenoresImportStatus.NEVER_STARTED);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(SECOND, FIRST);
  }

  // Ordering changes when an outcome is produced, never which one it is: a failure moved to the
  // end of the sweep still counts against the verdict exactly as the recorded order would have
  // counted it.
  @Test
  void counts_the_failing_organo_when_the_order_moved_it_to_the_end() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, ContratosMenoresImportStatus.NEVER_STARTED);
    catalogueHolds(SECOND, ContratosMenoresImportStatus.COMPLETE);
    eachOrganoSettles(
        organoId ->
            organoId.equals(FIRST)
                ? ImportRunOrganoState.FAILED
                : ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);
  }

  // ------------------------------------------------------------ the guard going

  // The run has been claimed by whoever triggered after this one went quiet. Its record is theirs
  // now, and this process settling it would report the work the live run is still doing as done.
  @Test
  void settles_nothing_at_all_when_the_run_stops_holding_the_guard_mid_sweep() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    theRunIsTakenOverAt(FIRST);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST);
    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // A sweep whose Órganos all need no walk never asks the guard through a walk, so without this
  // ask a run that went quiet part-way would still settle itself over the top of whoever claimed
  // the guard next — reporting as finished the work the live run is still doing.
  @Test
  void settles_nothing_when_the_run_stopped_being_the_live_one_before_the_verdict() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true).thenReturn(false);
    runIsRecordedCovering();

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // ---------------------------------------------------------------- verdicts

  @Test
  void records_the_run_as_succeeded_when_no_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_failed_when_every_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.FAILED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void records_the_run_as_partially_succeeded_when_some_failed_and_some_did_not() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(
        organoId ->
            organoId.equals(SECOND)
                ? ImportRunOrganoState.FAILED
                : ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);
  }

  // Nothing produces the skipped state any more, but runs recorded before the refresh existed
  // carry it. Read the other way round — nothing succeeded, therefore failed — such a run would
  // turn into a fault years after the fact, every time an administrator opened it.
  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_skipped() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SKIPPED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_stopped() {
    runCovers(FIRST, SECOND);
    catalogueHolds(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.STOPPED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_succeeded_when_it_covered_no_organo() {
    runCovers();

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
    verifyNoInteractions(coveredOrgano);
  }

  // ---------------------------------------------------------------- settling

  // Settled from a finally: a run left in progress by a process that has given up on it refuses
  // every import in the system until the abandonment bound passes.
  @Test
  void settles_the_run_as_failed_when_the_orchestration_itself_throws() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importRuns.findRun(RUN_ID)).thenThrow(new IllegalStateException("the record is gone"));
    ExecuteContratosMenoresImport execute = executeContratosMenoresImport();

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> execute.execute(RUN_ID));

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void returns_normally_when_the_run_itself_cannot_be_settled() {
    runCovers();
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .complete(any(), any(), anyInt(), anyInt());
    ExecuteContratosMenoresImport execute = executeContratosMenoresImport();

    execute.execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  /** A run this process holds the guard for, covering the given Órganos. */
  private void runCovers(OrganoId... organoIds) {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    runIsRecordedCovering(organoIds);
  }

  /** The coverage alone, for the tests that say themselves what the guard answers. */
  private void runIsRecordedCovering(OrganoId... organoIds) {
    runIsRecordedWithCoverage(
        Arrays.stream(organoIds).map(ExecuteContratosMenoresImportTest::pending).toList());
  }

  private void runIsRecordedWithCoverage(List<ImportRunOrganoCoverage> coverage) {
    when(importRuns.findRun(RUN_ID))
        .thenReturn(
            Optional.of(
                new ImportRunReport(
                    RUN_ID,
                    Importer.CONTRATOS_MENORES,
                    ImportRunState.IN_PROGRESS,
                    T_ZERO,
                    null,
                    0,
                    0,
                    coverage)));
  }

  private static ImportRunOrganoCoverage pending(OrganoId organoId) {
    return pending(organoId, ContractFamily.CONTRATOS_MENORES);
  }

  private static ImportRunOrganoCoverage pending(OrganoId organoId, ContractFamily family) {
    return new ImportRunOrganoCoverage(
        organoId, family, ImportRunOrganoState.PENDING, 0, 0, null);
  }

  /**
   * The one place the per-Órgano step is stubbed. Every turn records the Órgano it was for, so no
   * test can assert the order they were taken in against a recording it forgot to set up.
   */
  private void eachOrganoSettles(Function<OrganoId, ImportRunOrganoState> state) {
    when(coveredOrgano.run(eq(RUN_ID), any()))
        .thenAnswer(
            invocation -> {
              OrganoId organoId = invocation.getArgument(1);
              imported.add(organoId);
              return Optional.of(state.apply(organoId));
            });
  }

  /** As above, but the named Órgano's turn finds the run is no longer this process's to use. */
  private void theRunIsTakenOverAt(OrganoId taken) {
    when(coveredOrgano.run(eq(RUN_ID), any()))
        .thenAnswer(
            invocation -> {
              OrganoId organoId = invocation.getArgument(1);
              imported.add(organoId);
              return organoId.equals(taken)
                  ? Optional.empty()
                  : Optional.of(ImportRunOrganoState.SUCCEEDED);
            });
  }

  /** All of them in the catalogue at the same status, so the sweep keeps the recorded order. */
  private void catalogueHolds(OrganoId... organoIds) {
    for (OrganoId organoId : organoIds) {
      catalogueHolds(organoId, ContratosMenoresImportStatus.COMPLETE);
    }
  }

  private void catalogueHolds(OrganoId organoId, ContratosMenoresImportStatus status) {
    when(organos.findById(organoId)).thenReturn(Optional.of(organoAt(organoId, status)));
  }

  private void goneFromTheCatalogue(OrganoId organoId) {
    when(organos.findById(organoId)).thenReturn(Optional.empty());
  }

  private static OrganoDeContratacion organoAt(
      OrganoId organoId, ContratosMenoresImportStatus status) {
    ContratosMenoresImportState state =
        status == ContratosMenoresImportStatus.NEVER_STARTED
            ? null
            : new ContratosMenoresImportState(organoId, status, null, T_ZERO, null);
    return new OrganoDeContratacion(organoId, "source-key", "Órgano", true, true, null, state);
  }

  private ExecuteContratosMenoresImport executeContratosMenoresImport() {
    return new ExecuteContratosMenoresImport(importRuns, coveredOrgano, organos);
  }
}
