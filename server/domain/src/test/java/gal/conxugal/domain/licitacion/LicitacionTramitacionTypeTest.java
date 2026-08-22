package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicitacionTramitacionTypeTest {

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new LicitacionTramitacionType("Ordinaria").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_published_name() {
    LicitacionTramitacionTypeId id = new LicitacionTramitacionTypeId(UUID.randomUUID());

    LicitacionTramitacionType type = new LicitacionTramitacionType(id, "Ordinaria");

    assertThat(type.id()).isEqualTo(id);
    assertThat(type.name()).isEqualTo("Ordinaria");
  }

  @Test
  void keeps_the_name_exactly_as_published_including_internal_spacing() {
    String name = "Urxente   por acordo";

    assertThat(new LicitacionTramitacionType(name).name())
        .isEqualTo(name);
  }

  @Test
  void strips_an_untrimmed_name_rather_than_keying_an_entry_beside_the_trimmed_one() {
    assertThat(new LicitacionTramitacionType("  Ordinaria  ").name())
        .isEqualTo("Ordinaria");
  }

  @Test
  void keeps_two_published_spellings_apart_rather_than_folding_their_case() {
    assertThat(new LicitacionTramitacionType("Ordinaria").name())
        .isNotEqualTo(new LicitacionTramitacionType("ordinaria").name());
  }

  @Test
  void requires_the_published_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new LicitacionTramitacionType(null));
  }

  @Test
  void refuses_an_entry_keyed_on_blank_name() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LicitacionTramitacionType(""));
  }

  @Test
  void is_the_same_type_as_itself_whether_stored_or_not() {
    LicitacionTramitacionType identified =
        new LicitacionTramitacionType(
            new LicitacionTramitacionTypeId(UUID.randomUUID()), "Ordinaria");
    LicitacionTramitacionType sameIdentified = identified;
    LicitacionTramitacionType unstored = new LicitacionTramitacionType("Ordinaria");
    LicitacionTramitacionType sameUnstored = unstored;

    // Through a second reference rather than a literal self-comparison. The unstored half is the
    // one that matters: identity-only equality without the short-circuit would answer false.
    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_another_vocabulary_holding_the_same_name() {
    LicitacionTramitacionType identified =
        new LicitacionTramitacionType(
            new LicitacionTramitacionTypeId(UUID.randomUUID()), "Ordinaria");

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Ordinaria"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    LicitacionTramitacionType unstored = new LicitacionTramitacionType("Ordinaria");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  @Test
  void separates_types_carrying_different_identities() {
    LicitacionTramitacionType first =
        new LicitacionTramitacionType(
            new LicitacionTramitacionTypeId(UUID.randomUUID()), "Ordinaria");
    LicitacionTramitacionType second =
        new LicitacionTramitacionType(
            new LicitacionTramitacionTypeId(UUID.randomUUID()), "Ordinaria");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void treats_two_readings_of_one_stored_type_as_the_same_type() {
    LicitacionTramitacionTypeId id = new LicitacionTramitacionTypeId(UUID.randomUUID());

    LicitacionTramitacionType read = new LicitacionTramitacionType(id, "Ordinaria");
    LicitacionTramitacionType reread = new LicitacionTramitacionType(id, "Ordinaria");

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void treats_types_the_database_has_not_identified_as_distinct() {
    assertThat(new LicitacionTramitacionType("Ordinaria"))
        .isNotEqualTo(new LicitacionTramitacionType("Ordinaria"));
  }
}
