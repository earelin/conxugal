package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class OrganoResponseTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final TermoId TERMO_ID = new TermoId(UUID.randomUUID());

  @Test
  void unwraps_the_identities_classified_organo_carries() {
    OrganoResponse response = OrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "sanidade", "Consellería de Sanidade", true,
            TERMO_ID));

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.id()).isEqualTo(ORGANO_ID.value());
      softly.assertThat(response.name()).isEqualTo("Consellería de Sanidade");
      softly.assertThat(response.active()).isTrue();
      softly.assertThat(response.termoId()).isEqualTo(TERMO_ID.value());
    });
  }

  @Test
  void maps_unclassified_organo_to_null_termo_id() {
    OrganoResponse response = OrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, null));

    assertThat(response.termoId()).isNull();
  }

  @Test
  void carries_the_inactive_state_rather_than_dropping_the_organo() {
    OrganoResponse response = OrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", false, null));

    assertThat(response.active()).isFalse();
  }

  // The record allows a null id so an Órgano can be built before the database assigns one;
  // nothing unsaved ever reaches this read, so the mapping refuses it rather than answering
  // with a null identity a client would have to guess the meaning of.
  @Test
  void refuses_organo_that_was_never_persisted() {
    OrganoDeContratacion unsaved = new OrganoDeContratacion("mar", "Consellería do Mar");

    assertThatThrownBy(() -> OrganoResponse.of(unsaved))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must carry an id");
  }
}
