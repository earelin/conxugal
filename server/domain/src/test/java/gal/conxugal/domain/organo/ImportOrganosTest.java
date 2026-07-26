package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ImportOrganosTest {

  @Test
  void delegates_the_fetched_list_to_the_reconciler_and_returns_its_outcome() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    ImportOrganos importOrganos =
        new ImportOrganos(
            sourceReturning("consorcio-x", "Consorcio X"), new OrganoReconciler(repository));

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.sourceKey()).isEqualTo("consorcio-x"));
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_is_unavailable() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    repository.seed("consorcio-x", "Consorcio X", true);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> {
              throw new OrganoSourceUnavailableException("source is down");
            },
            new OrganoReconciler(repository));

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.active()).isTrue());
  }

  @Test
  void writes_nothing_and_reports_failure_when_the_source_returns_an_empty_list() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    repository.seed("consorcio-x", "Consorcio X", true);
    ImportOrganos importOrganos = new ImportOrganos(List::of, new OrganoReconciler(repository));

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.active()).isTrue());
  }

  @Test
  void returns_already_running_for_trigger_arriving_while_run_is_in_progress() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    AtomicReference<ImportOrganos> importOrganosRef = new AtomicReference<>();
    AtomicReference<ImportOutcome> nestedOutcome = new AtomicReference<>();
    OrganoSource reentrantSource =
        () -> {
          nestedOutcome.set(importOrganosRef.get().run());
          return List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X"));
        };
    ImportOrganos importOrganos =
        new ImportOrganos(reentrantSource, new OrganoReconciler(repository));
    importOrganosRef.set(importOrganos);

    ImportOutcome outcome = importOrganos.run();

    assertThat(nestedOutcome.get().status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
  }

  private static OrganoSource sourceReturning(String sourceKey, String name) {
    return () -> List.of(new OrganoSourceEntry(sourceKey, name));
  }
}
