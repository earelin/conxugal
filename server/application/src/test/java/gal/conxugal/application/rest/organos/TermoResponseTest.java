package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.Map;
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

  // The mapping producing a null parentId is only half of it: the serializer's default
  // inclusion drops a null, which would take parentId out of the payload altogether — where
  // the contract declares it required and a client groups by it to find the roots. Asserting
  // the serialized key rather than the record's field is what makes @JsonInclude(ALWAYS)
  // load-bearing; without it this reads back as {"id":…,"name":…} and fails.
  @Test
  void serialises_root_term_with_an_explicit_null_parent() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    TermoResponse response = TermoResponse.of(new Termo(TERMO_ID, "Sanidade", null));

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap)).containsEntry("parentId", null);
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
