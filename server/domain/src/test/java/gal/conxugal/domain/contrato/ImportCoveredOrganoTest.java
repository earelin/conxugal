package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratosMenoresImportSummary.StopReason;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * One Órgano's turn: whether it is still to be imported, which mode its history calls for, and the
 * row that says how it ended. Nothing here knows there are other Órganos.
 */
@ExtendWith(MockitoExtension.class)
class ImportCoveredOrganoTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");

  @Mock
  private OrganoRepository organos;

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private ImportOrganoContratosMenores walk;

  private final List<OrganoId> walked = new ArrayList<>();
  private final List<BooleanSupplier> eligibilityChecks = new ArrayList<>();

  // ---------------------------------------------------------------- the mode rule

  @Test
  void walks_the_organo_whose_import_never_started() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(walked).containsExactly(ORGANO_ID);
  }

  @Test
  void resumes_the_organo_whose_history_is_half_loaded() {
    organoIs(loaded(ContratosMenoresImportStatus.INCOMPLETE));
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(walked).containsExactly(ORGANO_ID);
  }

  // Neither imported nor failed, and saying so is the point: reported as either, a fully loaded
  // catalogue would read as a nightly failure or as work that never happened.
  @Test
  void skips_the_organo_whose_history_is_loaded_naming_the_mode_that_does_not_exist() {
    organoIs(loaded(ContratosMenoresImportStatus.COMPLETE));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.SKIPPED);
    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(ORGANO_ID),
            eq(ImportRunOrganoState.SKIPPED),
            argThat(reason -> reason != null && reason.contains("INCREMENTAL")));
    verifyNoInteractions(walk);
  }

  // A sweep reaches its last Órgano days after the run enumerated it. One unmarked in between must
  // have nothing read for it at all, not one page read before the walk notices.
  @Test
  void stops_the_organo_unmarked_before_its_turn_without_reading_its_source() {
    organoIs(organo(true, false));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.STOPPED);
    verifyNoInteractions(walk);
  }

  @Test
  void fails_the_organo_that_vanished_from_the_catalogue_before_its_turn() {
    when(organos.findById(ORGANO_ID)).thenReturn(Optional.empty());

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.FAILED);
    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(ORGANO_ID),
            eq(ImportRunOrganoState.FAILED),
            argThat(Objects::nonNull));
  }

  // ---------------------------------------------------------------- how a walk ended

  @Test
  void settles_the_organo_that_read_its_history_out_as_succeeded() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.SUCCEEDED);
    verify(importRuns).finishOrgano(RUN_ID, ORGANO_ID, ImportRunOrganoState.SUCCEEDED, null);
  }

  // The history floor is an ending of the walk's own, not a fault and not an interruption: what it
  // read stands, and the Órgano is resumed by a later run.
  @Test
  void settles_the_organo_that_reached_the_history_floor_as_succeeded() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.incomplete(100, 0));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.SUCCEEDED);
  }

  // Without this the two succeeding endings are the same row, and an Órgano stuck at the floor
  // reports as loaded on this run and on every run after it, with nothing saying otherwise.
  @Test
  void tells_the_organo_that_reached_the_history_floor_from_one_that_read_it_out() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.incomplete(100, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(ORGANO_ID),
            eq(ImportRunOrganoState.SUCCEEDED),
            argThat(Objects::nonNull));
  }

  @Test
  void settles_the_organo_unmarked_mid_walk_as_stopped_naming_what_stopped_it() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.stopped(100, 0, StopReason.UNMARKED));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.STOPPED);
    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(ORGANO_ID),
            eq(ImportRunOrganoState.STOPPED),
            argThat(Objects::nonNull));
  }

  // The two stops read alike on the row without this: one is an administrator's decision about one
  // Órgano, the other is this run having been replaced, and only the reason tells them apart.
  @Test
  void distinguishes_the_organo_unmarked_before_its_turn_from_one_unmarked_mid_walk() {
    List<String> reasons = new ArrayList<>();
    recordStopReasonsInto(reasons);
    when(organos.findById(ORGANO_ID))
        .thenReturn(Optional.of(marked()))
        .thenReturn(Optional.of(organo(true, false)));
    walkAnswers(organoId -> ContratosMenoresImportSummary.stopped(100, 0, StopReason.UNMARKED));
    ImportCoveredOrgano importCoveredOrgano = importCoveredOrgano();

    importCoveredOrgano.run(RUN_ID, ORGANO_ID);
    importCoveredOrgano.run(RUN_ID, ORGANO_ID);

    assertThat(reasons)
        .hasSize(2)
        .doesNotHaveDuplicates();
  }

  // The run is another import's now. Its record is left exactly as this process found it, which is
  // what an abandoned run is supposed to look like — so this Órgano's row is not written either.
  @Test
  void records_nothing_when_the_run_stopped_holding_the_guard_mid_walk() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.stopped(100, 0, StopReason.GUARD_LOST));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).isEmpty();
    verifyNoInteractions(importRuns);
  }

  // ------------------------------------------------ the walk's eligibility check

  @Test
  void tells_the_walk_the_organo_is_still_eligible_while_it_stays_marked() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(eligibilityChecks.getFirst().getAsBoolean()).isTrue();
  }

  @Test
  void tells_the_walk_the_organo_is_no_longer_eligible_once_it_is_unmarked() {
    when(organos.findById(ORGANO_ID))
        .thenReturn(Optional.of(marked()))
        .thenReturn(Optional.of(organo(true, false)));
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(eligibilityChecks.getFirst().getAsBoolean()).isFalse();
  }

  @Test
  void tells_the_walk_the_organo_is_no_longer_eligible_once_it_leaves_the_catalogue() {
    when(organos.findById(ORGANO_ID))
        .thenReturn(Optional.of(marked()))
        .thenReturn(Optional.empty());
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(eligibilityChecks.getFirst().getAsBoolean()).isFalse();
  }

  // ---------------------------------------------------------------- isolation

  // Everything the step can throw is caught, not only the source being unavailable: the run owes
  // the Órganos after this one that its failure costs only itself.
  @Test
  void settles_the_organo_whose_source_failed_as_failed_naming_the_reason() {
    organoIs(marked());
    walkAnswers(
        organoId -> {
          throw new ContratoMenorSourceUnavailableException("the source is unreachable");
        });

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.FAILED);
    verify(importRuns)
        .finishOrgano(
            RUN_ID, ORGANO_ID, ImportRunOrganoState.FAILED, "the source is unreachable");
  }

  // The message belongs to whatever threw, and it is stored on the row and served to an
  // administrator, so its length is this class's to bound rather than the source's to choose.
  @Test
  void caps_the_reason_it_records_for_an_unreasonably_long_failure() {
    organoIs(marked());
    walkAnswers(
        organoId -> {
          throw new IllegalStateException("x".repeat(5_000));
        });

    importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    verify(importRuns)
        .finishOrgano(
            eq(RUN_ID),
            eq(ORGANO_ID),
            eq(ImportRunOrganoState.FAILED),
            argThat(reason -> reason != null && reason.length() < 1_000));
  }

  // Reading the catalogue is inside the catch too: a momentary failure on that one row must not
  // abandon the Órganos after this one.
  @Test
  void settles_the_organo_as_failed_when_the_catalogue_cannot_be_read() {
    when(organos.findById(ORGANO_ID))
        .thenThrow(new IllegalStateException("the catalogue is unreachable"));

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.FAILED);
    verifyNoInteractions(walk);
  }

  // A settlement row is evidence about an import, never a participant in it: a run of four hundred
  // Órganos must not be abandoned at the first because one row could not be written.
  @Test
  void still_answers_how_the_organo_ended_when_its_row_cannot_be_written() {
    organoIs(marked());
    walkAnswers(organoId -> ContratosMenoresImportSummary.complete(1, 0));
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .finishOrgano(RUN_ID, ORGANO_ID, ImportRunOrganoState.SUCCEEDED, null);

    Optional<ImportRunOrganoState> settled = importCoveredOrgano().run(RUN_ID, ORGANO_ID);

    assertThat(settled).contains(ImportRunOrganoState.SUCCEEDED);
  }

  // ---------------------------------------------------------------- fixtures

  private ImportCoveredOrgano importCoveredOrgano() {
    return new ImportCoveredOrgano(organos, importRuns, walk);
  }

  private void organoIs(OrganoDeContratacion organo) {
    when(organos.findById(ORGANO_ID)).thenReturn(Optional.of(organo));
  }

  /**
   * The one place the walk is stubbed. Every walk records the Órgano it was for and the eligibility
   * check it was handed, whatever it goes on to answer.
   */
  private void walkAnswers(Function<OrganoId, ContratosMenoresImportSummary> answer) {
    when(walk.run(eq(RUN_ID), any(), any()))
        .thenAnswer(
            invocation -> {
              OrganoId organoId =
                  Objects.requireNonNull(
                      invocation.<OrganoDeContratacion>getArgument(1).id());
              walked.add(organoId);
              eligibilityChecks.add(invocation.getArgument(2));
              return answer.apply(organoId);
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

  private static OrganoDeContratacion marked() {
    return organo(true, true);
  }

  private static OrganoDeContratacion organo(boolean active, boolean importable) {
    return new OrganoDeContratacion(
        ORGANO_ID, "source-key", "Órgano", active, importable, null, null);
  }

  private static OrganoDeContratacion loaded(ContratosMenoresImportStatus status) {
    return new OrganoDeContratacion(
        ORGANO_ID,
        "source-key",
        "Órgano",
        true,
        true,
        null,
        new ContratosMenoresImportState(ORGANO_ID, status, null, T_ZERO));
  }
}
