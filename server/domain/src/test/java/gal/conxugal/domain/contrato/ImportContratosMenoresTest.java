package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratosMenoresImportSummary.StopReason;
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
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportContratosMenoresTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId FIRST = new OrganoId(UUID.randomUUID());
  private static final OrganoId SECOND = new OrganoId(UUID.randomUUID());
  private static final OrganoId THIRD = new OrganoId(UUID.randomUUID());
  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");

  @Mock
  private OrganoRepository organos;

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private ImportOrganoContratosMenores walk;

  private final List<OrganoId> walked = new ArrayList<>();

  // ---------------------------------------------------------------- claiming

  @Test
  void claims_one_run_covering_every_active_and_marked_organo() {
    when(organos.findAllByActiveTrueAndImportableTrue())
        .thenReturn(List.of(marked(FIRST), marked(SECOND)));
    // Stubbed on the exact coverage: strict stubbing refuses any other list, so this is what
    // proves the run was claimed over these two and no others.
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of(FIRST, SECOND)))
        .thenReturn(Optional.of(RUN_ID));

    ContratosMenoresImportClaim claim = importContratosMenores().claimAll();

    assertThat(claim).isEqualTo(ContratosMenoresImportClaim.claimed(RUN_ID));
  }

  // A catalogue with nothing marked is an ordinary answer, not a refusal: the run records that it
  // was asked and covered nothing, which is what an administrator seeing no import needs.
  @Test
  void claims_one_run_covering_nothing_when_no_organo_is_marked() {
    when(organos.findAllByActiveTrueAndImportableTrue()).thenReturn(List.of());
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of())).thenReturn(Optional.of(RUN_ID));

    ContratosMenoresImportClaim claim = importContratosMenores().claimAll();

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.CLAIMED);
  }

  @Test
  void refuses_the_sweep_when_another_import_holds_the_guard() {
    when(organos.findAllByActiveTrueAndImportableTrue()).thenReturn(List.of(marked(FIRST)));
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of(FIRST))).thenReturn(Optional.empty());

    ContratosMenoresImportClaim claim = importContratosMenores().claimAll();

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.ALREADY_RUNNING);
    assertThat(claim.runId()).isNull();
  }

  @Test
  void claims_one_run_covering_only_the_named_organo() {
    when(organos.findById(SECOND)).thenReturn(Optional.of(marked(SECOND)));
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of(SECOND)))
        .thenReturn(Optional.of(RUN_ID));

    ContratosMenoresImportClaim claim = importContratosMenores().claimOrgano(SECOND);

    assertThat(claim).isEqualTo(ContratosMenoresImportClaim.claimed(RUN_ID));
  }

  // The guard is never touched: an ineligible Órgano leaves no run row and no evidence of having
  // been asked for, and the refusal it gets is its own rather than whichever one the guard had.
  @Test
  void refuses_the_named_organo_that_is_not_marked_without_touching_the_guard() {
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, true, false)));

    ContratosMenoresImportClaim claim = importContratosMenores().claimOrgano(FIRST);

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.NOT_ELIGIBLE);
    verifyNoInteractions(importRuns);
  }

  @Test
  void refuses_the_named_organo_that_is_no_longer_active() {
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, false, true)));

    ContratosMenoresImportClaim claim = importContratosMenores().claimOrgano(FIRST);

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.NOT_ELIGIBLE);
    verifyNoInteractions(importRuns);
  }

  @Test
  void throws_when_the_named_organo_is_unknown() {
    when(organos.findById(FIRST)).thenReturn(Optional.empty());
    ImportContratosMenores importContratosMenores = importContratosMenores();

    assertThatExceptionOfType(OrganoNotFoundException.class)
        .isThrownBy(() -> importContratosMenores.claimOrgano(FIRST));

    verifyNoInteractions(importRuns);
  }

  // ---------------------------------------------------------------- walking

  // Serial, and in the order the run recorded: a run is working on one identifiable Órgano at any
  // moment, which is what makes its per-Órgano outcomes reportable.
  @Test
  void walks_the_covered_organos_one_after_another_in_the_order_the_run_holds_them() {
    runCovers(FIRST, SECOND, THIRD);
    organoIsMarked(FIRST, SECOND, THIRD);
    walkCompletesEveryOrgano();

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST, SECOND, THIRD);
  }

  @Test
  void walks_the_organo_whose_import_never_started() {
    runCovers(FIRST);
    when(organos.findById(FIRST)).thenReturn(Optional.of(marked(FIRST)));
    walkCompletesEveryOrgano();

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST);
  }

  @Test
  void resumes_the_organo_whose_history_is_half_loaded() {
    runCovers(FIRST);
    when(organos.findById(FIRST))
        .thenReturn(Optional.of(loaded(FIRST, ContratosMenoresImportStatus.INCOMPLETE)));
    walkCompletesEveryOrgano();

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST);
  }

  // Neither imported nor failed, and saying so is the point: reported as either, a fully loaded
  // catalogue would read as a nightly failure or as work that never happened.
  @Test
  void skips_the_organo_whose_history_is_loaded_naming_the_mode_that_does_not_exist() {
    runCovers(FIRST);
    when(organos.findById(FIRST))
        .thenReturn(Optional.of(loaded(FIRST, ContratosMenoresImportStatus.COMPLETE)));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(FIRST),
            eq(ImportRunOrganoState.SKIPPED),
            argThat(reason -> reason != null && reason.contains("INCREMENTAL")));
    verifyNoInteractions(walk);
  }

  // A sweep reaches its last Órgano days after the run enumerated it. One unmarked in between must
  // have nothing read for it at all, not one page read before the walk notices.
  @Test
  void stops_the_organo_unmarked_before_its_turn_without_reading_its_source() {
    runCovers(FIRST);
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, true, false)));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(FIRST),
            eq(ImportRunOrganoState.STOPPED),
            argThat(Objects::nonNull));
    verifyNoInteractions(walk);
  }

  @Test
  void settles_the_organo_unmarked_mid_walk_as_stopped_naming_what_stopped_it() {
    runCovers(FIRST);
    organoIsMarked(FIRST);
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenReturn(ContratosMenoresImportSummary.stopped(100, 0, StopReason.UNMARKED));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(FIRST),
            eq(ImportRunOrganoState.STOPPED),
            argThat(Objects::nonNull));
  }

  // The two stops read alike on the row without this: one is an administrator's decision about one
  // Órgano, the other is this run having been replaced, and only the reason tells them apart.
  @Test
  void distinguishes_the_organo_unmarked_before_its_turn_from_one_unmarked_mid_walk() {
    runCovers(FIRST, SECOND);
    when(organos.findById(FIRST)).thenReturn(Optional.of(marked(FIRST)));
    when(organos.findById(SECOND)).thenReturn(Optional.of(organo(SECOND, true, false)));
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenReturn(ContratosMenoresImportSummary.stopped(100, 0, StopReason.UNMARKED));
    List<String> reasons = new ArrayList<>();
    recordStopReasonsInto(reasons);

    importContratosMenores().execute(RUN_ID);

    assertThat(reasons).hasSize(2).doesNotHaveDuplicates();
  }

  @Test
  void settles_the_organo_that_read_its_history_out_as_succeeded() {
    runCovers(FIRST);
    organoIsMarked(FIRST);
    walkCompletesEveryOrgano();

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).finishOrgano(RUN_ID, FIRST, ImportRunOrganoState.SUCCEEDED, null);
  }

  // The history floor is an ending of the walk's own, not a fault and not an interruption: what it
  // read stands, and the Órgano is resumed by a later run.
  @Test
  void settles_the_organo_that_reached_the_history_floor_as_succeeded() {
    runCovers(FIRST);
    organoIsMarked(FIRST);
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenReturn(ContratosMenoresImportSummary.incomplete(100, 0));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).finishOrgano(RUN_ID, FIRST, ImportRunOrganoState.SUCCEEDED, null);
  }

  @Test
  void fails_the_organo_that_vanished_from_the_catalogue_before_its_turn() {
    runCovers(FIRST);
    when(organos.findById(FIRST)).thenReturn(Optional.empty());

    importContratosMenores().execute(RUN_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(FIRST),
            eq(ImportRunOrganoState.FAILED),
            argThat(Objects::nonNull));
  }

  // ------------------------------------------------ the walk's eligibility check

  @Test
  void tells_the_walk_the_organo_is_still_eligible_while_it_stays_marked() {
    runCovers(FIRST);
    organoIsMarked(FIRST);
    BooleanSupplier stillEligible = eligibilityCheckHandedToTheWalk();

    importContratosMenores().execute(RUN_ID);

    assertThat(stillEligible.getAsBoolean()).isTrue();
  }

  @Test
  void tells_the_walk_the_organo_is_no_longer_eligible_once_it_is_unmarked() {
    runCovers(FIRST);
    when(organos.findById(FIRST))
        .thenReturn(Optional.of(marked(FIRST)))
        .thenReturn(Optional.of(organo(FIRST, true, false)));
    BooleanSupplier stillEligible = eligibilityCheckHandedToTheWalk();

    importContratosMenores().execute(RUN_ID);

    assertThat(stillEligible.getAsBoolean()).isFalse();
  }

  @Test
  void tells_the_walk_the_organo_is_no_longer_eligible_once_it_leaves_the_catalogue() {
    runCovers(FIRST);
    when(organos.findById(FIRST))
        .thenReturn(Optional.of(marked(FIRST)))
        .thenReturn(Optional.empty());
    BooleanSupplier stillEligible = eligibilityCheckHandedToTheWalk();

    importContratosMenores().execute(RUN_ID);

    assertThat(stillEligible.getAsBoolean()).isFalse();
  }

  // ---------------------------------------------------------------- isolation

  @Test
  void carries_on_to_the_next_organo_when_one_organos_source_fails() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST, SECOND);
    walkFailsOn(FIRST);

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST, SECOND);
    verify(importRuns).finishOrgano(RUN_ID, SECOND, ImportRunOrganoState.SUCCEEDED, null);
  }

  @Test
  void names_the_reason_on_the_organo_whose_source_failed() {
    runCovers(FIRST);
    organoIsMarked(FIRST);
    walkFailsOn(FIRST);

    importContratosMenores().execute(RUN_ID);

    verify(importRuns)
        .finishOrgano(RUN_ID, FIRST, ImportRunOrganoState.FAILED, "the source is unreachable");
  }

  // A settlement row is evidence about an import, never a participant in it: a run of four hundred
  // Órganos must not be abandoned at the first because one row could not be written.
  @Test
  void carries_on_when_one_organos_settlement_cannot_be_recorded() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST, SECOND);
    walkCompletesEveryOrgano();
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .finishOrgano(RUN_ID, FIRST, ImportRunOrganoState.SUCCEEDED, null);

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST, SECOND);
    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  // ---------------------------------------------------------------- verdicts

  @Test
  void records_the_run_as_succeeded_when_no_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST, SECOND);
    walkCompletesEveryOrgano();

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_failed_when_every_covered_organo_failed() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST, SECOND);
    walkFailsOn(FIRST, SECOND);

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void records_the_run_as_partially_succeeded_when_some_failed_and_some_did_not() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST, SECOND);
    walkFailsOn(SECOND);

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);
  }

  // Nothing was imported and nothing failed. Read the other way round — nothing succeeded,
  // therefore failed — this is the ordinary nightly state of a loaded catalogue reported as a
  // fault, which is the lie the skipped state exists to avoid.
  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_skipped() {
    runCovers(FIRST, SECOND);
    when(organos.findById(FIRST))
        .thenReturn(Optional.of(loaded(FIRST, ContratosMenoresImportStatus.COMPLETE)));
    when(organos.findById(SECOND))
        .thenReturn(Optional.of(loaded(SECOND, ContratosMenoresImportStatus.COMPLETE)));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_succeeded_when_every_covered_organo_was_stopped() {
    runCovers(FIRST, SECOND);
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, true, false)));
    when(organos.findById(SECOND)).thenReturn(Optional.of(organo(SECOND, true, false)));

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  @Test
  void records_the_run_as_succeeded_when_it_covered_no_organo() {
    runCovers();

    importContratosMenores().execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
    verifyNoInteractions(organos, walk);
  }

  // ------------------------------------------------------------ the guard going

  // The run has been claimed by whoever triggered after this one went quiet. Its record is theirs
  // now, and this process settling it would report the work the live run is still doing as done.
  @Test
  void settles_nothing_at_all_when_the_run_stops_holding_the_guard_mid_walk() {
    runCovers(FIRST, SECOND);
    organoIsMarked(FIRST);
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenAnswer(
            invocation -> {
              walked.add(organoIdOf(invocation.getArgument(1)));
              return ContratosMenoresImportSummary.stopped(100, 0, StopReason.GUARD_LOST);
            });

    importContratosMenores().execute(RUN_ID);

    assertThat(walked).containsExactly(FIRST);
    verify(importRuns, never()).finishOrgano(any(), any(), any(), any());
    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // Asked to execute a run that is already over — a retry, or a redelivered trigger. Without this
  // it would walk the coverage again and overwrite the verdict and the Órganos it named as failed.
  @Test
  void reads_no_organo_when_the_run_it_was_handed_is_no_longer_the_live_one() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(false);

    importContratosMenores().execute(RUN_ID);

    verifyNoInteractions(organos, walk);
    verify(importRuns, never()).complete(any(), any(), anyInt(), anyInt());
  }

  // ---------------------------------------------------------------- settling

  // Settled from a finally: a run left in progress by a process that has given up on it refuses
  // every import in the system until the abandonment bound passes.
  @Test
  void settles_the_run_as_failed_when_the_orchestration_itself_throws() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importRuns.findRun(RUN_ID)).thenThrow(new IllegalStateException("the record is gone"));
    ImportContratosMenores importContratosMenores = importContratosMenores();

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> importContratosMenores.execute(RUN_ID));

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void returns_normally_when_the_run_itself_cannot_be_settled() {
    runCovers();
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .complete(any(), any(), anyInt(), anyInt());
    ImportContratosMenores importContratosMenores = importContratosMenores();

    importContratosMenores.execute(RUN_ID);

    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 0, 0);
  }

  // Defence rather than a reachable state — the guard and the coverage are read off the same row —
  // but a run that answers no coverage must walk nothing rather than fall through to the catalogue.
  @Test
  void reads_no_organo_when_the_run_it_was_handed_is_not_recorded() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importRuns.findRun(RUN_ID)).thenReturn(Optional.empty());

    importContratosMenores().execute(RUN_ID);

    verifyNoInteractions(organos, walk);
  }

  // ---------------------------------------------------------------- fixtures

  private ImportContratosMenores importContratosMenores() {
    return new ImportContratosMenores(organos, importRuns, walk);
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

  private void organoIsMarked(OrganoId... organoIds) {
    for (OrganoId organoId : organoIds) {
      when(organos.findById(organoId)).thenReturn(Optional.of(marked(organoId)));
    }
  }

  /** Records which Órgano each walk was for, and answers a walk that read the history out. */
  private void walkCompletesEveryOrgano() {
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenAnswer(
            invocation -> {
              walked.add(organoIdOf(invocation.getArgument(1)));
              return ContratosMenoresImportSummary.complete(1, 0);
            });
  }

  /** As above, but the named Órganos answer an unreachable source instead. */
  private void walkFailsOn(OrganoId... failing) {
    List<OrganoId> unreachable = List.of(failing);
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenAnswer(
            invocation -> {
              OrganoId organoId = organoIdOf(invocation.getArgument(1));
              walked.add(organoId);
              if (unreachable.contains(organoId)) {
                throw new ContratoMenorSourceUnavailableException("the source is unreachable");
              }
              return ContratosMenoresImportSummary.complete(1, 0);
            });
  }

  /** Collects the reason recorded on every Órgano settled as stopped. */
  private void recordStopReasonsInto(List<String> reasons) {
    doAnswer(
            invocation -> {
              reasons.add(invocation.getArgument(3));
              return null;
            })
        .when(importRuns)
        .finishOrgano(eq(RUN_ID), any(), eq(ImportRunOrganoState.STOPPED), any());
  }

  /** Hands back the check the walk was given, so the test can ask it what the walk would ask. */
  private BooleanSupplier eligibilityCheckHandedToTheWalk() {
    List<BooleanSupplier> handed = new ArrayList<>();
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenAnswer(
            invocation -> {
              handed.add(invocation.getArgument(2));
              return ContratosMenoresImportSummary.complete(1, 0);
            });
    return () -> handed.getFirst().getAsBoolean();
  }

  private static OrganoId organoIdOf(OrganoDeContratacion organo) {
    return Objects.requireNonNull(organo.id());
  }

  private static OrganoDeContratacion marked(OrganoId organoId) {
    return organo(organoId, true, true);
  }

  private static OrganoDeContratacion organo(
      OrganoId organoId, boolean active, boolean importable) {
    return new OrganoDeContratacion(
        organoId, "source-key", "Órgano", active, importable, null, null);
  }

  private static OrganoDeContratacion loaded(
      OrganoId organoId, ContratosMenoresImportStatus status) {
    return new OrganoDeContratacion(
        organoId,
        "source-key",
        "Órgano",
        true,
        true,
        null,
        new ContratosMenoresImportState(organoId, status, null, T_ZERO));
  }
}
