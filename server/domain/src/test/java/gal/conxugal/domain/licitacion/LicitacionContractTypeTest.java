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
  void refuses_an_untrimmed_name_rather_than_keying_an_entry_beside_the_trimmed_one() {
    // The name is the natural key, so padding the adapter failed to strip would store a second
    // vocabulary row for one published type, each with its own procedures hanging off it.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LicitacionContractType("  Obras  "));
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
  void is_the_same_type_as_itself_whether_stored_or_not() {
    LicitacionContractType identified =
        new LicitacionContractType(new LicitacionContractTypeId(UUID.randomUUID()), "Obras");
    LicitacionContractType sameIdentified = identified;
    LicitacionContractType unstored = new LicitacionContractType("Obras");
    LicitacionContractType sameUnstored = unstored;

    // Through a second reference rather than a literal self-comparison. The unstored half is the
    // one that matters: identity-only equality without the short-circuit would answer false.
    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_another_vocabulary_holding_the_same_name() {
    LicitacionContractType identified =
        new LicitacionContractType(new LicitacionContractTypeId(UUID.randomUUID()), "Obras");

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionProcedureType("Obras"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    LicitacionContractType unstored = new LicitacionContractType("Obras");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  @Test
  void separates_types_carrying_different_identities() {
    LicitacionContractType first =
        new LicitacionContractType(new LicitacionContractTypeId(UUID.randomUUID()), "Obras");
    LicitacionContractType second =
        new LicitacionContractType(new LicitacionContractTypeId(UUID.randomUUID()), "Obras");

    assertThat(first).isNotEqualTo(second);
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
