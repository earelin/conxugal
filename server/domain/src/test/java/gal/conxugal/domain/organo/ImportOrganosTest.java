package gal.conxugal.domain.organo;

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
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportOrganosTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final List<OrganoSourceEntry> SOURCE_LIST =
      List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X"));

  @Mock
  private OrganoSource organoSource;

  @Mock
  private OrganoReconciler organoReconciler;

  @Mock
  private ImportRunRepository importRuns;

  @Test
  void delegates_the_fetched_list_to_the_reconciler_and_returns_its_outcome() {
    guardIsFree();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);
    ImportOutcome reconciled = ImportOutcome.success(1, 0, 0);
    when(organoReconciler.reconcile(SOURCE_LIST)).thenReturn(reconciled);

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome).isEqualTo(reconciled);
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_is_unavailable() {
    guardIsFree();
    when(organoSource.fetchAll())
        .thenThrow(new OrganoSourceUnavailableException("source is down"));

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    verify(organoReconciler, never()).reconcile(any());
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_returns_an_empty_list() {
    guardIsFree();
    when(organoSource.fetchAll()).thenReturn(List.of());

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    verify(organoReconciler, never()).reconcile(any());
  }

  @Test
  void reconciles_nothing_when_another_import_of_either_kind_holds_the_guard() {
    when(importRuns.claim(Importer.ORGANOS, List.of())).thenReturn(Optional.empty());

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    verifyNoInteractions(organoSource, organoReconciler);
  }

  @Test
  void records_succeeded_with_the_added_and_refreshed_counts_but_not_deactivated() {
    guardIsFree();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);
    when(organoReconciler.reconcile(SOURCE_LIST)).thenReturn(ImportOutcome.success(3, 2, 7));

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome.deactivated()).isEqualTo(7);
    verify(importRuns).complete(RUN_ID, ImportRunState.SUCCEEDED, 3, 2);
  }

  @Test
  void records_the_run_as_failed_when_the_source_is_unavailable() {
    guardIsFree();
    when(organoSource.fetchAll())
        .thenThrow(new OrganoSourceUnavailableException("source is down"));

    importOrganos().run();

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void records_the_run_as_failed_and_rethrows_when_the_reconciliation_itself_throws() {
    guardIsFree();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);
    RuntimeException reconciliationFailure = new IllegalStateException("a write went wrong");
    when(organoReconciler.reconcile(SOURCE_LIST)).thenThrow(reconciliationFailure);
    ImportOrganos importOrganos = importOrganos();

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(importOrganos::run)
        .isSameAs(reconciliationFailure);

    verify(importRuns).complete(RUN_ID, ImportRunState.FAILED, 0, 0);
  }

  @Test
  void reports_the_outcome_as_it_stands_when_the_run_record_cannot_be_settled() {
    guardIsFree();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);
    ImportOutcome reconciled = ImportOutcome.success(3, 2, 7);
    when(organoReconciler.reconcile(SOURCE_LIST)).thenReturn(reconciled);
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .complete(eq(RUN_ID), any(), anyInt(), anyInt());

    ImportOutcome outcome = importOrganos().run();

    assertThat(outcome).isEqualTo(reconciled);
  }

  private void guardIsFree() {
    when(importRuns.claim(Importer.ORGANOS, List.of())).thenReturn(Optional.of(RUN_ID));
  }

  private ImportOrganos importOrganos() {
    return new ImportOrganos(organoSource, organoReconciler, importRuns);
  }
}
