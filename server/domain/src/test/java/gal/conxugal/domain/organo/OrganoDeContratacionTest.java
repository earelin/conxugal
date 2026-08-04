package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.organo.taxonomia.TermoId;
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
}
