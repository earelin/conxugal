package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicitacionProcedureTypeTest {

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new LicitacionProcedureType("Abertos").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_published_name() {
    LicitacionProcedureTypeId id = new LicitacionProcedureTypeId(UUID.randomUUID());

    LicitacionProcedureType type = new LicitacionProcedureType(id, "Abertos");

    assertThat(type.id()).isEqualTo(id);
    assertThat(type.name()).isEqualTo("Abertos");
  }

  @Test
  void keeps_the_name_exactly_as_published_including_internal_spacing() {
    String name = "Negociados   sen publicidade";

    assertThat(new LicitacionProcedureType(name).name())
        .isEqualTo(name);
  }

  @Test
  void keeps_the_name_exactly_as_the_adapter_handed_it_over() {
    assertThat(new LicitacionProcedureType("  Abertos  ").name())
        .isEqualTo("  Abertos  ");
  }

  @Test
  void keeps_two_published_spellings_apart_rather_than_folding_their_case() {
    assertThat(new LicitacionProcedureType("Abertos").name())
        .isNotEqualTo(new LicitacionProcedureType("abertos").name());
  }

  @Test
  void requires_the_published_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new LicitacionProcedureType(null));
  }

  @Test
  void refuses_an_entry_keyed_on_blank_name() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LicitacionProcedureType("   "));
  }

  @Test
  void treats_two_readings_of_one_stored_type_as_the_same_type() {
    LicitacionProcedureTypeId id = new LicitacionProcedureTypeId(UUID.randomUUID());

    LicitacionProcedureType read = new LicitacionProcedureType(id, "Abertos");
    LicitacionProcedureType reread = new LicitacionProcedureType(id, "Abertos");

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void treats_types_the_database_has_not_identified_as_distinct() {
    assertThat(new LicitacionProcedureType("Abertos"))
        .isNotEqualTo(new LicitacionProcedureType("Abertos"));
  }
}
