package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganoDeContratacionTest {

  @Test
  void exposes_id_source_key_name_acronym_and_active() {
    UUID id = UUID.randomUUID();
    OrganoDeContratacion organo =
        new OrganoDeContratacion(id, "xunta-consorcio-galego", "Consorcio Galego", "CG", true);

    assertThat(organo.id()).isEqualTo(id);
    assertThat(organo.sourceKey()).isEqualTo("xunta-consorcio-galego");
    assertThat(organo.name()).isEqualTo("Consorcio Galego");
    assertThat(organo.acronym()).isEqualTo("CG");
    assertThat(organo.active()).isTrue();
  }

  @Test
  void allows_null_id_before_being_persisted() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(null, "xunta-consorcio-galego", "Consorcio Galego", "CG", true);

    assertThat(organo.id()).isNull();
  }

  @Test
  void allows_null_acronym() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(
            UUID.randomUUID(), "xunta-consorcio-galego", "Consorcio Galego", null, true);

    assertThat(organo.acronym()).isNull();
  }

  @Test
  void rejects_null_source_key() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new OrganoDeContratacion(
                    UUID.randomUUID(), null, "Consorcio Galego", "CG", true));
  }

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new OrganoDeContratacion(
                    UUID.randomUUID(), "xunta-consorcio-galego", null, "CG", true));
  }

  @Test
  void newly_discovered_organo_has_no_id_and_starts_active() {
    OrganoDeContratacion organo =
        new OrganoDeContratacion("xunta-consorcio-galego", "Consorcio Galego", "CG");

    assertThat(organo.id()).isNull();
    assertThat(organo.sourceKey()).isEqualTo("xunta-consorcio-galego");
    assertThat(organo.name()).isEqualTo("Consorcio Galego");
    assertThat(organo.acronym()).isEqualTo("CG");
    assertThat(organo.active()).isTrue();
  }
}
