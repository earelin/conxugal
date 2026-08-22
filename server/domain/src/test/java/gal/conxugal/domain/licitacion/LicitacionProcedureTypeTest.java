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
  void strips_an_untrimmed_name_rather_than_keying_an_entry_beside_the_trimmed_one() {
    assertThat(new LicitacionProcedureType("  Abertos  ").name())
        .isEqualTo("Abertos");
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
  void is_the_same_type_as_itself_whether_stored_or_not() {
    LicitacionProcedureType identified =
        new LicitacionProcedureType(new LicitacionProcedureTypeId(UUID.randomUUID()), "Abertos");
    LicitacionProcedureType sameIdentified = identified;
    LicitacionProcedureType unstored = new LicitacionProcedureType("Abertos");
    LicitacionProcedureType sameUnstored = unstored;

    // Through a second reference rather than a literal self-comparison. The unstored half is the
    // one that matters: identity-only equality without the short-circuit would answer false.
    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_another_vocabulary_holding_the_same_name() {
    LicitacionProcedureType identified =
        new LicitacionProcedureType(new LicitacionProcedureTypeId(UUID.randomUUID()), "Abertos");

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionTramitacionType("Abertos"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    LicitacionProcedureType unstored = new LicitacionProcedureType("Abertos");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  @Test
  void separates_types_carrying_different_identities() {
    LicitacionProcedureType first =
        new LicitacionProcedureType(new LicitacionProcedureTypeId(UUID.randomUUID()), "Abertos");
    LicitacionProcedureType second =
        new LicitacionProcedureType(new LicitacionProcedureTypeId(UUID.randomUUID()), "Abertos");

    assertThat(first).isNotEqualTo(second);
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
