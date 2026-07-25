package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ImportOrganosTest {

  @Test
  void adds_new_source_entry_as_inactive() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")), repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.sourceKey()).isEqualTo("consorcio-x");
              assertThat(organo.name()).isEqualTo("Consorcio X");
              assertThat(organo.active()).isFalse();
            });
  }

  @Test
  void refreshes_matched_entrys_name_in_place_preserving_its_id() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Old Name", true);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> List.of(new OrganoSourceEntry("consorcio-x", "New Name")), repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.name()).isEqualTo("New Name");
            });
  }

  @Test
  void refreshing_an_inactive_matched_entrys_name_does_not_reactivate_it() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Old Name", false);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> List.of(new OrganoSourceEntry("consorcio-x", "New Name")), repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.name()).isEqualTo("New Name");
              assertThat(organo.active()).isFalse();
            });
  }

  @Test
  void matching_an_entry_with_an_unchanged_name_writes_nothing_and_leaves_its_state_untouched() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", false);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")), repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.refreshed()).isZero();
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.active()).isFalse();
            });
  }

  @Test
  void marks_stored_entry_absent_from_the_source_inactive_and_keeps_it() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", true);
    ImportOrganos importOrganos = new ImportOrganos(List::of, repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.deactivated()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.active()).isFalse();
            });
  }

  @Test
  void reimporting_the_same_list_adds_nothing_and_changes_no_state() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    ImportOrganos importOrganos =
        new ImportOrganos(
            () -> List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")), repository);
    importOrganos.run();

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.added()).isZero();
    assertThat(outcome.refreshed()).isZero();
    assertThat(outcome.deactivated()).isZero();
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.active()).isFalse());
  }

  @Test
  void reports_added_refreshed_and_deactivated_counts_together() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    repository.seed("stays", "Stays", true);
    repository.seed("goes-inactive", "Goes Inactive", true);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () ->
                List.of(
                    new OrganoSourceEntry("stays", "Stays Renamed"),
                    new OrganoSourceEntry("new-one", "New One")),
            repository);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(outcome.deactivated()).isEqualTo(1);
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
            repository);

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
    ImportOrganos importOrganos = new ImportOrganos(reentrantSource, repository);
    importOrganosRef.set(importOrganos);

    ImportOutcome outcome = importOrganos.run();

    assertThat(nestedOutcome.get().status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
  }

  private static final class FakeOrganoRepository implements OrganoRepository {

    private final List<OrganoDeContratacion> stored = new ArrayList<>();

    @Override
    public List<OrganoDeContratacion> findAll() {
      return List.copyOf(stored);
    }

    @Override
    public List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys) {
      return stored.stream().filter(organo -> sourceKeys.contains(organo.sourceKey())).toList();
    }

    @Override
    public OrganoDeContratacion insert(OrganoDeContratacion organo) {
      OrganoDeContratacion withId =
          new OrganoDeContratacion(
              UUID.randomUUID(), organo.sourceKey(), organo.name(), organo.active());
      stored.add(withId);
      return withId;
    }

    @Override
    public void update(UUID id, String name, boolean active) {
      replace(id, organo -> new OrganoDeContratacion(id, organo.sourceKey(), name, active));
    }

    @Override
    public void updateActive(UUID id, boolean active) {
      replace(
          id, organo -> new OrganoDeContratacion(id, organo.sourceKey(), organo.name(), active));
    }

    OrganoDeContratacion seed(String sourceKey, String name, boolean active) {
      OrganoDeContratacion organo =
          new OrganoDeContratacion(UUID.randomUUID(), sourceKey, name, active);
      stored.add(organo);
      return organo;
    }

    private void replace(UUID id, Function<OrganoDeContratacion, OrganoDeContratacion> mapper) {
      for (int i = 0; i < stored.size(); i++) {
        if (id.equals(stored.get(i).id())) {
          stored.set(i, mapper.apply(stored.get(i)));
          return;
        }
      }
      throw new IllegalStateException("No stored Órgano with id " + id);
    }
  }
}
