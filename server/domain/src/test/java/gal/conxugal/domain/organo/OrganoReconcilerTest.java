package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrganoReconcilerTest {

  @Test
  void adds_new_source_entry_as_active() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.sourceKey()).isEqualTo("consorcio-x");
              assertThat(organo.name()).isEqualTo("Consorcio X");
              assertThat(organo.active()).isTrue();
            });
  }

  @Test
  void refreshes_matched_entrys_name_in_place_preserving_its_id() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Old Name", true);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "New Name")));

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
  void refreshing_deactivated_matched_entrys_name_also_reactivates_it() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Old Name", false);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "New Name")));

    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.name()).isEqualTo("New Name");
              assertThat(organo.active()).isTrue();
            });
  }

  @Test
  void matching_deactivated_entry_with_unchanged_name_reactivates_it() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", false);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.active()).isTrue();
            });
  }

  @Test
  void matching_an_active_entry_with_an_unchanged_name_writes_nothing() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", true);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.refreshed()).isZero();
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.active()).isTrue();
            });
  }

  @Test
  void collapses_duplicate_source_keys_in_one_payload_into_single_insert() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(
            List.of(
                new OrganoSourceEntry("consorcio-x", "First Name"),
                new OrganoSourceEntry("consorcio-x", "Last Name")));

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.name()).isEqualTo("Last Name"));
  }

  @Test
  void marks_stored_entry_absent_from_the_source_inactive_and_keeps_it() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", true);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("other-key", "Other Org")));

    assertThat(outcome.deactivated()).isEqualTo(1);
    assertThat(repository.findAll())
        .filteredOn(organo -> "consorcio-x".equals(organo.sourceKey()))
        .singleElement()
        .satisfies(
            organo -> {
              assertThat(organo.id()).isEqualTo(stored.id());
              assertThat(organo.active()).isFalse();
            });
  }

  @Test
  void redeactivating_an_already_inactive_absentee_reports_zero_deactivated() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    OrganoDeContratacion stored = repository.seed("consorcio-x", "Consorcio X", true);
    OrganoReconciler reconciler = new OrganoReconciler(repository);
    List<OrganoSourceEntry> entries = List.of(new OrganoSourceEntry("other-key", "Other Org"));
    ImportOutcome firstOutcome = reconciler.reconcile(entries);

    ImportOutcome secondOutcome = reconciler.reconcile(entries);

    assertThat(firstOutcome.deactivated()).isEqualTo(1);
    assertThat(secondOutcome.deactivated()).isZero();
    assertThat(repository.findAll())
        .filteredOn(organo -> "consorcio-x".equals(organo.sourceKey()))
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
    OrganoReconciler reconciler = new OrganoReconciler(repository);
    List<OrganoSourceEntry> entries =
        List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X"));
    reconciler.reconcile(entries);

    ImportOutcome outcome = reconciler.reconcile(entries);

    assertThat(outcome.added()).isZero();
    assertThat(outcome.refreshed()).isZero();
    assertThat(outcome.deactivated()).isZero();
    assertThat(repository.findAll())
        .singleElement()
        .satisfies(organo -> assertThat(organo.active()).isTrue());
  }

  @Test
  void reports_added_refreshed_and_deactivated_counts_together() {
    FakeOrganoRepository repository = new FakeOrganoRepository();
    repository.seed("stays", "Stays", true);
    repository.seed("goes-inactive", "Goes Inactive", true);
    OrganoReconciler reconciler = new OrganoReconciler(repository);

    ImportOutcome outcome =
        reconciler.reconcile(
            List.of(
                new OrganoSourceEntry("stays", "Stays Renamed"),
                new OrganoSourceEntry("new-one", "New One")));

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(outcome.refreshed()).isEqualTo(1);
    assertThat(outcome.deactivated()).isEqualTo(1);
  }
}
