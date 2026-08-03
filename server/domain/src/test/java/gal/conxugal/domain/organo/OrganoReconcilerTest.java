package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganoReconcilerTest {

  @Mock
  private OrganoRepository organoRepository;

  private OrganoReconciler reconciler;

  @BeforeEach
  void setUp() {
    reconciler = new OrganoReconciler(organoRepository);
  }

  @Test
  void adds_new_source_entry_as_active() {
    when(organoRepository.findAllOrderByName()).thenReturn(List.of());
    ArgumentCaptor<OrganoDeContratacion> inserted = ArgumentCaptor.forClass(
        OrganoDeContratacion.class);

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    verify(organoRepository).insert(inserted.capture());
    assertThat(inserted.getValue().sourceKey()).isEqualTo("consorcio-x");
    assertThat(inserted.getValue().name()).isEqualTo("Consorcio X");
    assertThat(inserted.getValue().active()).isTrue();
  }

  @Test
  void refreshes_matched_entrys_name_in_place_preserving_its_id() {
    OrganoDeContratacion stored = organo("consorcio-x", "Old Name", true);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stored));

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "New Name")));

    assertThat(outcome.refreshed()).isEqualTo(1);
    verify(organoRepository).update(stored.id(), "New Name", true);
  }

  @Test
  void refreshing_deactivated_matched_entrys_name_also_reactivates_it() {
    OrganoDeContratacion stored = organo("consorcio-x", "Old Name", false);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stored));

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "New Name")));

    assertThat(outcome.refreshed()).isEqualTo(1);
    verify(organoRepository).update(stored.id(), "New Name", true);
  }

  @Test
  void matching_deactivated_entry_with_unchanged_name_reactivates_it() {
    OrganoDeContratacion stored = organo("consorcio-x", "Consorcio X", false);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stored));

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.refreshed()).isEqualTo(1);
    verify(organoRepository).update(stored.id(), "Consorcio X", true);
  }

  @Test
  void matching_an_active_entry_with_an_unchanged_name_writes_nothing() {
    OrganoDeContratacion stored = organo("consorcio-x", "Consorcio X", true);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stored));

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("consorcio-x", "Consorcio X")));

    assertThat(outcome.refreshed()).isZero();
    verify(organoRepository, never()).update(any(), any(), anyBoolean());
    verify(organoRepository, never()).insert(any());
    verify(organoRepository, never()).updateActive(any(), anyBoolean());
  }

  @Test
  void collapses_duplicate_source_keys_in_one_payload_into_single_insert() {
    when(organoRepository.findAllOrderByName()).thenReturn(List.of());
    ArgumentCaptor<OrganoDeContratacion> inserted = ArgumentCaptor.forClass(
        OrganoDeContratacion.class);

    ImportOutcome outcome =
        reconciler.reconcile(
            List.of(
                new OrganoSourceEntry("consorcio-x", "First Name"),
                new OrganoSourceEntry("consorcio-x", "Last Name")));

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(1);
    verify(organoRepository, times(1)).insert(inserted.capture());
    assertThat(inserted.getValue().name()).isEqualTo("Last Name");
  }

  @Test
  void marks_stored_entry_absent_from_the_source_inactive_and_keeps_it() {
    OrganoDeContratacion stored = organo("consorcio-x", "Consorcio X", true);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stored));

    ImportOutcome outcome =
        reconciler.reconcile(List.of(new OrganoSourceEntry("other-key", "Other Org")));

    assertThat(outcome.deactivated()).isEqualTo(1);
    verify(organoRepository).updateActive(stored.id(), false);
  }

  @Test
  void redeactivating_an_already_inactive_absentee_reports_zero_deactivated() {
    OrganoDeContratacion active = organo("consorcio-x", "Consorcio X", true);
    OrganoDeContratacion nowInactive =
        new OrganoDeContratacion(
            active.id(), active.sourceKey(), active.name(), false, false, null);
    when(organoRepository.findAllOrderByName())
        .thenReturn(List.of(active))
        .thenReturn(List.of(nowInactive));
    List<OrganoSourceEntry> entries = List.of(new OrganoSourceEntry("other-key", "Other Org"));

    ImportOutcome firstOutcome = reconciler.reconcile(entries);
    ImportOutcome secondOutcome = reconciler.reconcile(entries);

    assertThat(firstOutcome.deactivated()).isEqualTo(1);
    assertThat(secondOutcome.deactivated()).isZero();
    verify(organoRepository, times(1)).updateActive(active.id(), false);
  }

  @Test
  void reimporting_the_same_list_adds_nothing_and_changes_no_state() {
    OrganoSourceEntry entry = new OrganoSourceEntry("consorcio-x", "Consorcio X");
    OrganoDeContratacion inserted = organo("consorcio-x", "Consorcio X", true);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of()).thenReturn(List.of(inserted));

    reconciler.reconcile(List.of(entry));
    ImportOutcome outcome = reconciler.reconcile(List.of(entry));

    assertThat(outcome.added()).isZero();
    assertThat(outcome.refreshed()).isZero();
    assertThat(outcome.deactivated()).isZero();
    verify(organoRepository, times(1)).insert(any());
    verify(organoRepository, never()).update(any(), any(), anyBoolean());
    verify(organoRepository, never()).updateActive(any(), anyBoolean());
  }

  @Test
  void reports_added_refreshed_and_deactivated_counts_together() {
    OrganoDeContratacion stays = organo("stays", "Stays", true);
    OrganoDeContratacion goesInactive = organo("goes-inactive", "Goes Inactive", true);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(stays, goesInactive));

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

  // The mark is an administrator's decision, so reconciliation must have no way of touching
  // it: the only proof a mocked repository can give is that the write is never reached.
  @Test
  void reconciling_never_writes_the_import_mark() {
    OrganoDeContratacion markedAndRefreshed = organo("refreshed", "Old Name", true, true);
    OrganoDeContratacion markedAndAbsent = organo("absent", "Absent", true, true);
    OrganoDeContratacion markedAndInactive = organo("reactivated", "Reactivated", false, true);
    when(organoRepository.findAllOrderByName())
        .thenReturn(List.of(markedAndRefreshed, markedAndAbsent, markedAndInactive));

    reconciler.reconcile(
        List.of(
            new OrganoSourceEntry("refreshed", "New Name"),
            new OrganoSourceEntry("reactivated", "Reactivated"),
            new OrganoSourceEntry("brand-new", "Brand New")));

    verify(organoRepository, never()).updateImportable(any(), anyBoolean());
  }

  private static OrganoDeContratacion organo(String sourceKey, String name, boolean active) {
    return organo(sourceKey, name, active, false);
  }

  private static OrganoDeContratacion organo(
      String sourceKey, String name, boolean active, boolean importable) {
    return new OrganoDeContratacion(
        new OrganoId(UUID.randomUUID()), sourceKey, name, active, importable, null);
  }
}
