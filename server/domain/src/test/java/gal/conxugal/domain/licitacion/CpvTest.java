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

class CpvTest {

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(Arrays.stream(Cpv.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("id", "code", "description");
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new Cpv("45000000").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_regulated_code() {
    CpvId id = new CpvId(UUID.randomUUID());

    Cpv entry = new Cpv(id, "45000000", "Construction work");

    assertThat(entry.id()).isEqualTo(id);
    assertThat(entry.code()).isEqualTo("45000000");
  }

  @Test
  void is_met_without_the_description_because_the_record_publishes_the_code_alone() {
    assertThat(new Cpv("45000000").description())
        .isNull();
  }

  @Test
  void keeps_two_entries_sharing_one_description_apart() {
    // The description is wording, not identity: sibling entries of the regulated list are
    // routinely described alike, so a store unique on it would reject a real entry and matching
    // on it would merge two the list distinguishes.
    Cpv first = new Cpv(new CpvId(UUID.randomUUID()), "45000000", "Construction work");
    Cpv second = new Cpv(new CpvId(UUID.randomUUID()), "45100000", "Construction work");

    assertThat(first.description())
        .isEqualTo(second.description());
    assertThat(new HashSet<>(List.of(first, second)))
        .hasSize(2);
  }

  @Test
  void constructs_with_the_code_the_table_has_never_held_because_the_list_is_versioned() {
    // Regulated is not closed: the 2008 revision retired codes the 2003 one issued, and this
    // system imports procedures published across both. An unseen code costs a row rather than a
    // rejected procedure.
    assertThat(new Cpv("29800000").code())
        .isEqualTo("29800000");
  }

  @Test
  void strips_an_untrimmed_code_rather_than_keying_an_entry_beside_the_trimmed_one() {
    assertThat(new Cpv(" 45000000 ").code())
        .isEqualTo("45000000");
  }

  @Test
  void keeps_the_code_exactly_as_published_including_its_separator() {
    // The check digit the list appends is part of the published code; nothing here reformats it,
    // so a source that publishes it and one that does not key two entries rather than one.
    assertThat(new Cpv("45000000-7").code())
        .isEqualTo("45000000-7");
  }

  @Test
  void holds_the_description_that_published_only_whitespace_as_null() {
    assertThat(new Cpv(new CpvId(UUID.randomUUID()), "45000000", " \t").description())
        .isNull();
  }

  @Test
  void requires_the_regulated_code() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Cpv(null));
  }

  @Test
  void refuses_an_entry_keyed_on_blank_code() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Cpv(" \t"));
  }

  @Test
  void is_the_same_entry_as_itself_whether_stored_or_not() {
    Cpv identified = new Cpv(new CpvId(UUID.randomUUID()), "45000000", null);
    Cpv sameIdentified = identified;
    Cpv unstored = new Cpv("45000000");
    Cpv sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_the_nut_entry_holding_the_same_code() {
    Cpv identified = new Cpv(new CpvId(UUID.randomUUID()), "45000000", null);

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new Nut("45000000"));
  }

  @Test
  void treats_two_readings_of_one_stored_entry_as_the_same_entry() {
    CpvId id = new CpvId(UUID.randomUUID());

    assertThat(new Cpv(id, "45000000", null))
        .isEqualTo(new Cpv(id, "45000000", null))
        .hasSameHashCodeAs(new Cpv(id, "45000000", null));
  }

  @Test
  void stays_the_same_entry_when_its_description_is_supplied_underneath() {
    CpvId id = new CpvId(UUID.randomUUID());

    Cpv beforeWording = new Cpv(id, "45000000", null);
    Cpv afterWording = new Cpv(id, "45000000", "Construction work");

    assertThat(beforeWording)
        .isEqualTo(afterWording)
        .hasSameHashCodeAs(afterWording);
  }

  @Test
  void treats_entries_the_database_has_not_identified_as_distinct() {
    assertThat(new Cpv("45000000"))
        .isNotEqualTo(new Cpv("45000000"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Cpv unstored = new Cpv("45000000");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }
}
