package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NutTest {

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(Arrays.stream(Nut.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("id", "code", "description");
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new Nut("ES111").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_regulated_code() {
    NutId id = new NutId(UUID.randomUUID());

    Nut entry = new Nut(id, "ES111", "A Coruña");

    assertThat(entry.id()).isEqualTo(id);
    assertThat(entry.code()).isEqualTo("ES111");
  }

  @Test
  void is_met_without_the_description_because_the_record_publishes_the_code_alone() {
    assertThat(new Nut("ES111").description())
        .isNull();
  }

  @Test
  void keeps_two_entries_sharing_one_description_apart() {
    Nut first = new Nut(new NutId(UUID.randomUUID()), "ES111", "Galicia");
    Nut second = new Nut(new NutId(UUID.randomUUID()), "ES11", "Galicia");

    assertThat(first.description())
        .isEqualTo(second.description());
    assertThat(new HashSet<>(List.of(first, second)))
        .hasSize(2);
  }

  @Test
  void strips_an_untrimmed_code_rather_than_keying_an_entry_beside_the_trimmed_one() {
    assertThat(new Nut(" ES111 ").code())
        .isEqualTo("ES111");
  }

  @Test
  void keeps_two_published_spellings_apart_rather_than_folding_their_case() {
    assertThat(new Nut("ES111").code())
        .isNotEqualTo(new Nut("es111").code());
  }

  @Test
  void holds_the_description_that_published_only_whitespace_as_null() {
    assertThat(new Nut(new NutId(UUID.randomUUID()), "ES111", " \t").description())
        .isNull();
  }

  @Test
  void requires_the_regulated_code() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Nut(null));
  }

  @Test
  void refuses_an_entry_keyed_on_blank_code() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Nut(" \t"));
  }

  @Test
  void is_the_same_entry_as_itself_whether_stored_or_not() {
    Nut identified = new Nut(new NutId(UUID.randomUUID()), "ES111", null);
    Nut sameIdentified = identified;
    Nut unstored = new Nut("ES111");
    Nut sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_the_cpv_entry_holding_the_same_code() {
    Nut identified = new Nut(new NutId(UUID.randomUUID()), "45000000", null);

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new Cpv("45000000"));
  }

  @Test
  void treats_two_readings_of_one_stored_entry_as_the_same_entry() {
    NutId id = new NutId(UUID.randomUUID());

    assertThat(new Nut(id, "ES111", null))
        .isEqualTo(new Nut(id, "ES111", null))
        .hasSameHashCodeAs(new Nut(id, "ES111", null));
  }

  @Test
  void stays_the_same_entry_when_its_description_is_supplied_underneath() {
    NutId id = new NutId(UUID.randomUUID());

    Nut beforeWording = new Nut(id, "ES111", null);
    Nut afterWording = new Nut(id, "ES111", "A Coruña");

    assertThat(beforeWording)
        .isEqualTo(afterWording)
        .hasSameHashCodeAs(afterWording);
  }

  @Test
  void treats_entries_the_database_has_not_identified_as_distinct() {
    assertThat(new Nut("ES111"))
        .isNotEqualTo(new Nut("ES111"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Nut unstored = new Nut("ES111");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }
}
