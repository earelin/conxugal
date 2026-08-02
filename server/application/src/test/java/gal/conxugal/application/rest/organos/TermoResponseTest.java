package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class TermoResponseTest {

  private static final TermoId TERMO_ID = new TermoId(UUID.randomUUID());
  private static final TermoId PARENT_ID = new TermoId(UUID.randomUUID());

  @Test
  void unwraps_the_identities_child_term_carries() {
    TermoResponse response = TermoResponse.of(new Termo(TERMO_ID, "Hospitais", PARENT_ID));

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.id()).isEqualTo(TERMO_ID.value());
      softly.assertThat(response.name()).isEqualTo("Hospitais");
      softly.assertThat(response.parentId()).isEqualTo(PARENT_ID.value());
    });
  }

  @Test
  void maps_root_term_to_null_parent_id() {
    TermoResponse response = TermoResponse.of(new Termo(TERMO_ID, "Sanidade", null));

    assertThat(response.parentId()).isNull();
  }

  // The record allows a null id so a term can be built before the database assigns one;
  // nothing unsaved ever reaches this read, so the mapping refuses it rather than answering
  // with a null identity the tree could not be assembled from.
  @Test
  void refuses_term_that_was_never_persisted() {
    Termo unsaved = new Termo("Sanidade", null);

    assertThatThrownBy(() -> TermoResponse.of(unsaved))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must carry an id");
  }
}
