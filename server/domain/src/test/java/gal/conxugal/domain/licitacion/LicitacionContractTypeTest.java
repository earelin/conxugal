package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicitacionContractTypeTest {

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new LicitacionContractType("Obras").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_published_name() {
    LicitacionContractTypeId id = new LicitacionContractTypeId(UUID.randomUUID());

    LicitacionContractType type = new LicitacionContractType(id, "Obras");

    assertThat(type.id()).isEqualTo(id);
    assertThat(type.name()).isEqualTo("Obras");
  }

  @Test
  void keeps_the_name_exactly_as_published_including_internal_spacing() {
    String name = "Servizos   especializados";

    assertThat(new LicitacionContractType(name).name())
        .isEqualTo(name);
  }

  @Test
  void keeps_two_published_spellings_apart_rather_than_folding_their_case() {
    // Folding them would assert an equivalence the source never published, which is what storing
    // values as published forbids.
    assertThat(new LicitacionContractType("Obras").name())
        .isNotEqualTo(new LicitacionContractType("obras").name());
  }

  @Test
  void requires_the_published_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new LicitacionContractType(null));
  }

  @Test
  void refuses_an_entry_keyed_on_blank_name() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LicitacionContractType(" \t"));
  }

  @Test
  void treats_two_readings_of_one_stored_type_as_the_same_type() {
    LicitacionContractTypeId id = new LicitacionContractTypeId(UUID.randomUUID());

    LicitacionContractType read = new LicitacionContractType(id, "Obras");
    LicitacionContractType reread = new LicitacionContractType(id, "Obras");

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void treats_types_the_database_has_not_identified_as_distinct() {
    assertThat(new LicitacionContractType("Obras"))
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }
}
