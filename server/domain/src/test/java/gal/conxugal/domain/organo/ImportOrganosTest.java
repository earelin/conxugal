package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportOrganosTest {

  @Mock
  private OrganoSource organoSource;

  @Mock
  private OrganoReconciler organoReconciler;

  @Test
  void delegates_the_fetched_list_to_the_reconciler_and_returns_its_outcome() {
    List<OrganoSourceEntry> entries = List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X"));
    when(organoSource.fetchAll()).thenReturn(entries);
    ImportOutcome reconciled = ImportOutcome.success(1, 0, 0);
    when(organoReconciler.reconcile(entries)).thenReturn(reconciled);
    ImportOrganos importOrganos = new ImportOrganos(organoSource, organoReconciler);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome).isEqualTo(reconciled);
    verify(organoReconciler).reconcile(entries);
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_is_unavailable() {
    when(organoSource.fetchAll())
        .thenThrow(new OrganoSourceUnavailableException("source is down"));
    ImportOrganos importOrganos = new ImportOrganos(organoSource, organoReconciler);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    verify(organoReconciler, never()).reconcile(any());
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_returns_an_empty_list() {
    when(organoSource.fetchAll()).thenReturn(List.of());
    ImportOrganos importOrganos = new ImportOrganos(organoSource, organoReconciler);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    verify(organoReconciler, never()).reconcile(any());
  }

  @Test
  void returns_already_running_for_trigger_arriving_while_run_is_in_progress() {
    AtomicReference<ImportOrganos> importOrganosRef = new AtomicReference<>();
    AtomicReference<ImportOutcome> nestedOutcome = new AtomicReference<>();
    when(organoSource.fetchAll())
        .thenAnswer(
            invocation -> {
              nestedOutcome.set(importOrganosRef.get().run());
              return List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X"));
            });
    when(organoReconciler.reconcile(any()))
        .thenReturn(ImportOutcome.success(1, 0, 0));
    ImportOrganos importOrganos = new ImportOrganos(organoSource, organoReconciler);
    importOrganosRef.set(importOrganos);

    ImportOutcome outcome = importOrganos.run();

    assertThat(nestedOutcome.get().status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
  }
}
