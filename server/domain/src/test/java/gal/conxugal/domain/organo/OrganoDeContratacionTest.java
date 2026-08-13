package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganoDeContratacionTest {

  @Test
  void exposes_id_source_key_name_active_and_import_mark() {
    OrganoId id = new OrganoId(UUID.randomUUID());
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            id, "xunta-consorcio-galego", "Consorcio Galego", true, false, null);

    assertThat(organo.id()).isEqualTo(id);
    assertThat(organo.sourceKey()).isEqualTo("xunta-consorcio-galego");
    assertThat(organo.name()).isEqualTo("Consorcio Galego");
    assertThat(organo.active()).isTrue();
    assertThat(organo.importable()).isFalse();
  }

  // The absence of a state row is read here rather than at each call site, so a half-loaded
  // Órgano can never be mistaken for one that is up to date by a caller that forgot the rule.
  @Test
  void an_organo_carrying_no_import_state_has_never_started_importing() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "consorcio", "Consorcio Galego", true, true, null);

    assertThat(organo.importState()).isNull();
    assertThat(organo.importStatus()).isEqualTo(ContratosMenoresImportStatus.NEVER_STARTED);
  }

  @Test
  void an_organo_carrying_an_import_state_reports_that_state() {
    OrganoId id = new OrganoId(UUID.randomUUID());
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            id, "consorcio", "Consorcio Galego", true, true, null,
            ContratosMenoresImportState.startedAt(id, Instant.parse("2026-08-06T09:00:00Z")));

    assertThat(organo.importStatus()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
  }

  @Test
  void an_organo_whose_history_is_loaded_reports_it_complete() {
    OrganoId id = new OrganoId(UUID.randomUUID());
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            id,
            "consorcio",
            "Consorcio Galego",
            true,
            true,
            null,
            new ContratosMenoresImportState(
                id,
                ContratosMenoresImportStatus.COMPLETE,
                null,
                Instant.parse("2026-08-06T09:00:00Z")));

    assertThat(organo.importStatus()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
  }

  // Eligibility is the pair, and each half is asserted alone because either one standing in for
  // both is a mistake nothing else would catch: reading only the mark imports Órganos the source
  // has dropped, and reading only the catalogue imports every Órgano in it.
  @Test
  void is_eligible_for_import_when_active_and_marked() {
    assertThat(organoThatIs(true, true).eligibleForImport()).isTrue();
  }

  @Test
  void is_not_eligible_for_import_when_active_but_not_marked() {
    assertThat(organoThatIs(true, false).eligibleForImport()).isFalse();
  }

  @Test
  void is_not_eligible_for_import_when_marked_but_no_longer_active() {
    assertThat(organoThatIs(false, true).eligibleForImport()).isFalse();
  }

  @Test
  void is_not_eligible_for_import_when_neither_active_nor_marked() {
    assertThat(organoThatIs(false, false).eligibleForImport()).isFalse();
  }

  // Discovery marks nothing, so the Órgano the catalogue import creates is not one to import
  // contratos menores for until an administrator says so.
  @Test
  void newly_discovered_organo_is_not_eligible_for_import() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");

    assertThat(organo.eligibleForImport()).isFalse();
  }

  @Test
  void allows_null_id_before_being_persisted() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            null, "xunta-consorcio-galego", "Consorcio Galego", true, false, null);

    assertThat(organo.id()).isNull();
  }

  @Test
  void rejects_null_source_key() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new OrganoDeContratacion(
                    new OrganoId(UUID.randomUUID()), null, "Consorcio Galego", true, false, null));
  }

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new OrganoDeContratacion(
                    new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", null, true, false,
                    null));
  }

  @Test
  void newly_discovered_organo_has_no_id_and_starts_active() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");

    assertThat(organo.id()).isNull();
    assertThat(organo.sourceKey()).isEqualTo("xunta-consorcio-galego");
    assertThat(organo.name()).isEqualTo("Consorcio Galego");
    assertThat(organo.active()).isTrue();
  }

  @Test
  void carries_at_most_one_term_placement() {
    TermoId termoId = new TermoId(UUID.randomUUID());
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", "Consorcio Galego", true,
            false, termoId);

    assertThat(organo.termoId()).isEqualTo(termoId);
  }

  @Test
  void unclassified_by_default_when_built_without_placement() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");

    assertThat(organo.termoId()).isNull();
  }

  @Test
  void newly_discovered_organo_is_not_marked_for_import() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");

    assertThat(organo.importable()).isFalse();
  }

  @Test
  void an_organo_renamed_deactivated_and_reclassified_is_still_the_same_organo() {
    OrganoId id = new OrganoId(UUID.randomUUID());
    OrganoDeContratacion before =
        new OrganoDeContratacion(id, "xunta-consorcio-galego", "Consorcio Galego", true, false,
            null);
    OrganoDeContratacion after =
        new OrganoDeContratacion(id, "xunta-consorcio-galego", "Consorcio Galego de Benestar",
            false, true, new TermoId(UUID.randomUUID()));

    assertThat(before).isEqualTo(after);
    assertThat(before).hasSameHashCodeAs(after);
  }

  @Test
  void organos_under_different_ids_are_different_organos_whatever_their_source_key() {
    OrganoDeContratacion one =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", "Consorcio Galego", true,
            false, null);
    OrganoDeContratacion other =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", "Consorcio Galego", true,
            false, null);

    assertThat(one).isNotEqualTo(other);
  }

  @Test
  void undiscovered_organos_are_equal_to_nothing_but_themselves() {
    OrganoDeContratacion one =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");
    OrganoDeContratacion other =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");

    assertThat(one).isNotEqualTo(other);
    assertThat(Set.of(one, other))
        .hasSize(2)
        .contains(one, other);
  }

  // The identity comparison reaches for the other Órgano's id, so what it does with something that
  // has none at all is worth stating rather than inferring from the instanceof.
  @Test
  void matches_neither_null_nor_something_that_is_not_an_organo() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", "Consorcio Galego", true,
            false, null);

    assertThat(organo).isNotEqualTo(null);
    assertThat(organo).isNotEqualTo("xunta-consorcio-galego");
  }

  @Test
  void an_undiscovered_organo_matches_no_catalogued_one_in_either_direction() {
    OrganoDeContratacion undiscovered =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego");
    OrganoDeContratacion catalogued =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "xunta-consorcio-galego", "Consorcio Galego", true,
            false, null);

    assertThat(undiscovered).isNotEqualTo(catalogued);
    assertThat(catalogued).isNotEqualTo(undiscovered);
  }

  private static OrganoDeContratacion organoThatIs(boolean active, boolean importable) {
    return new OrganoDeContratacion(
        new OrganoId(UUID.randomUUID()),
        "xunta-consorcio-galego",
        "Consorcio Galego",
        active,
        importable,
        null);
  }
}
