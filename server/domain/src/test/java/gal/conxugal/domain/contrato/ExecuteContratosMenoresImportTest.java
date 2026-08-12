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

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoId;
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
  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private ImportCoveredOrgano coveredOrgano;

  private final List<OrganoId> imported = new ArrayList<>();

  // Serial, and in the order the run recorded: a run is working on one identifiable Órgano at any
  // moment, which is what makes its per-Órgano outcomes reportable.
  @Test
  void imports_the_covered_organos_one_after_another_in_the_order_the_run_holds_them() {
    runCovers(FIRST, SECOND, THIRD);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST, SECOND, THIRD);
  }

  @Test
  void carries_on_to_the_next_organo_when_one_of_them_failed() {
    runCovers(FIRST, SECOND);
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

  // ------------------------------------------------------------ the guard going

  // The run has been claimed by whoever triggered after this one went quiet. Its record is theirs
  // now, and this process settling it would report the work the live run is still doing as done.
  @Test
  void settles_nothing_at_all_when_the_run_stops_holding_the_guard_mid_sweep() {
    runCovers(FIRST, SECOND);
    theRunIsTakenOverAt(FIRST);

    executeContratosMenoresImport().execute(RUN_ID);

    assertThat(imported).containsExactly(FIRST);
    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // ---------------------------------------------------------------- verdicts

  @Test
  void records_the_run_as_succeeded_when_no_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_failed_when_every_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.FAILED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void records_the_run_as_partially_succeeded_when_some_failed_and_some_did_not() {
    runCovers(FIRST, SECOND);
    eachOrganoSettles(
        organoId ->
            organoId.equals(SECOND)
                ? ImportRunOrganoState.FAILED
                : ImportRunOrganoState.SUCCEEDED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);
  }

  // Nothing was imported and nothing failed. Read the other way round — nothing succeeded,
  // therefore failed — this is the ordinary nightly state of a loaded catalogue reported as a
  // fault, which is the lie the skipped state exists to avoid.
  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_skipped() {
    runCovers(FIRST, SECOND);
    eachOrganoSettles(organoId -> ImportRunOrganoState.SKIPPED);

    executeContratosMenoresImport().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_stopped() {
    runCovers(FIRST, SECOND);
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

  private void runCovers(OrganoId... organoIds) {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    List<ImportRunOrganoCoverage> coverage =
        Arrays.stream(organoIds)
            .map(
                organoId ->
                    new ImportRunOrganoCoverage(
                        organoId, ImportRunOrganoState.PENDING, 0, 0, null))
            .toList();
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

  private ExecuteContratosMenoresImport executeContratosMenoresImport() {
    return new ExecuteContratosMenoresImport(importRuns, coveredOrgano);
  }
}
