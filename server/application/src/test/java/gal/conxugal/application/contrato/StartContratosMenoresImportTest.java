package gal.conxugal.application.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ClaimContratosMenoresImport;
import gal.conxugal.domain.contrato.ExecuteContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pairing the claim with the walk, and choosing the thread the walk happens on. */
@ExtendWith(MockitoExtension.class)
class StartContratosMenoresImportTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());

  @Mock
  private ClaimContratosMenoresImport claim;

  @Mock
  private ExecuteContratosMenoresImport execute;

  @Mock
  private ImportRunRepository importRuns;

  private final List<Runnable> handedOff = new ArrayList<>();

  /** Collects rather than runs, so a test says when the walking happens. */
  private Executor executor;

  @BeforeEach
  void collectTheWorkHandedOff() {
    executor = handedOff::add;
  }

  // The claim's answer is the trigger's answer, and it must arrive without waiting for a walk that
  // takes days — so the identity comes back with the walking still queued.
  @Test
  void answers_the_run_identity_of_the_sweep_before_any_of_it_has_been_walked() {
    when(claim.claimAll()).thenReturn(RUN_ID);

    ImportRunId runId = startContratosMenoresImport().startAll();

    assertThat(runId).isEqualTo(RUN_ID);
    assertThat(handedOff).hasSize(1);
    verify(execute, never()).execute(RUN_ID);
  }

  @Test
  void answers_the_run_identity_of_one_organo_before_it_has_been_walked() {
    when(claim.claimOrgano(ORGANO_ID)).thenReturn(RUN_ID);

    ImportRunId runId = startContratosMenoresImport().startOrgano(ORGANO_ID);

    assertThat(runId).isEqualTo(RUN_ID);
    assertThat(handedOff).hasSize(1);
  }

  @Test
  void walks_the_run_it_claimed_once_the_executor_reaches_it() {
    when(claim.claimAll()).thenReturn(RUN_ID);

    startContratosMenoresImport().startAll();
    handedOff.forEach(Runnable::run);

    verify(execute).execute(RUN_ID);
  }

  // Nothing is caught on the way out: each refusal has a handler that turns it into the response
  // it deserves, and one of them means something else entirely on the mark endpoint.
  @Test
  void lets_the_guard_refusal_out_without_handing_anything_off() {
    when(claim.claimAll()).thenThrow(new ImportAlreadyRunningException());
    StartContratosMenoresImport start = startContratosMenoresImport();

    assertThatExceptionOfType(ImportAlreadyRunningException.class).isThrownBy(start::startAll);

    assertThat(handedOff).isEmpty();
    verifyNoInteractions(importRuns);
  }

  @Test
  void lets_the_not_eligible_refusal_out_without_handing_anything_off() {
    when(claim.claimOrgano(ORGANO_ID))
        .thenThrow(new OrganoNotEligibleForImportException(ORGANO_ID));
    StartContratosMenoresImport start = startContratosMenoresImport();

    assertThatExceptionOfType(OrganoNotEligibleForImportException.class)
        .isThrownBy(() -> start.startOrgano(ORGANO_ID));

    assertThat(handedOff).isEmpty();
    verifyNoInteractions(importRuns);
  }

  // The run is claimed by the time the executor refuses it. Left as it stands it would hold the
  // system-wide guard until the abandonment bound passed, for work no thread is going to do.
  @Test
  void settles_the_run_it_claimed_when_the_walking_cannot_be_handed_off() {
    when(claim.claimAll()).thenReturn(RUN_ID);
    executor =
        command -> {
          throw new RejectedExecutionException("the import executor is shut down");
        };
    StartContratosMenoresImport start = startContratosMenoresImport();

    assertThatExceptionOfType(RejectedExecutionException.class).isThrownBy(start::startAll);

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void lets_the_rejection_out_even_when_the_run_it_claimed_cannot_be_settled() {
    when(claim.claimAll()).thenReturn(RUN_ID);
    executor =
        command -> {
          throw new RejectedExecutionException("the import executor is shut down");
        };
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .complete(RUN_ID, ImportRunState.FAILED, 0, 0);
    StartContratosMenoresImport start = startContratosMenoresImport();

    assertThatExceptionOfType(RejectedExecutionException.class).isThrownBy(start::startAll);
  }

  // On the executor's thread there is nothing above this to catch anything, and what the run left
  // behind was recorded before it threw — so a failed walk must not take the thread down with it.
  @Test
  void swallows_what_the_walking_throws_on_the_executors_thread() {
    when(claim.claimAll()).thenReturn(RUN_ID);
    doThrow(new IllegalStateException("the record is gone")).when(execute).execute(RUN_ID);

    startContratosMenoresImport().startAll();

    assertThatCode(() -> handedOff.forEach(Runnable::run)).doesNotThrowAnyException();
  }

  private StartContratosMenoresImport startContratosMenoresImport() {
    return new StartContratosMenoresImport(claim, execute, importRuns, executor);
  }
}
