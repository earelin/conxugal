package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicitacionStateTest {

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(new LicitacionState(8, "Formalizado").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_published_code() {
    LicitacionStateId id = new LicitacionStateId(UUID.randomUUID());

    LicitacionState state = new LicitacionState(id, 8, "Formalizado");

    assertThat(state.id()).isEqualTo(id);
    assertThat(state.code()).isEqualTo(8);
  }

  @Test
  void keeps_two_states_sharing_one_label_apart() {
    // 101 and 102 are both published as Histórico, so the label is not a key: a store unique on
    // it would reject a real state and a filter keyed on it would merge two the source
    // distinguishes. Constructing the second is not refused for repeating the first's label, and
    // the two do not collapse where values are held by equality.
    LicitacionState historic =
        new LicitacionState(new LicitacionStateId(UUID.randomUUID()), 101, "Histórico");
    LicitacionState alsoHistoric =
        new LicitacionState(new LicitacionStateId(UUID.randomUUID()), 102, "Histórico");

    assertThat(historic.label())
        .isEqualTo(alsoHistoric.label());
    assertThat(Set.of(historic, alsoHistoric))
        .hasSize(2);
  }

  @Test
  void constructs_with_an_unseen_code_because_the_published_set_is_not_closed() {
    // Code 7 was never observed and higher codes may exist. Nothing validates against a known
    // set, so an unseen code costs a row rather than a rejected procedure.
    LicitacionState unseen = new LicitacionState(7, null);

    assertThat(unseen.code()).isEqualTo(7);
    assertThat(unseen.label()).isNull();
  }

  @Test
  void keeps_the_label_exactly_as_published_including_internal_spacing() {
    String label = "Anulado/Desestimado";

    assertThat(new LicitacionState(6, label).label())
        .isEqualTo(label);
  }

  @Test
  void holds_the_label_that_published_only_whitespace_as_null() {
    assertThat(new LicitacionState(4, " \t").label())
        .isNull();
  }

  @Test
  void is_the_same_state_as_itself_whether_stored_or_not() {
    LicitacionState identified =
        new LicitacionState(new LicitacionStateId(UUID.randomUUID()), 8, "Formalizado");
    LicitacionState sameIdentified = identified;
    LicitacionState unstored = new LicitacionState(8, "Formalizado");
    LicitacionState sameUnstored = unstored;

    // Through a second reference rather than a literal self-comparison. The unstored half is the
    // one that matters: identity-only equality without the short-circuit would answer false.
    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_anything_that_is_not_state() {
    LicitacionState identified =
        new LicitacionState(new LicitacionStateId(UUID.randomUUID()), 8, "Formalizado");

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    LicitacionState unstored = new LicitacionState(8, "Formalizado");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  @Test
  void treats_two_readings_of_one_stored_state_as_the_same_state() {
    LicitacionStateId id = new LicitacionStateId(UUID.randomUUID());

    LicitacionState read = new LicitacionState(id, 101, "Histórico");
    LicitacionState reread = new LicitacionState(id, 101, "Histórico");

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void stays_the_same_state_when_its_label_is_corrected_underneath() {
    LicitacionStateId id = new LicitacionStateId(UUID.randomUUID());

    LicitacionState before = new LicitacionState(id, 4, "Adxudicado");
    LicitacionState after = new LicitacionState(id, 4, "Adxudicado provisionalmente");

    assertThat(before)
        .isEqualTo(after)
        .hasSameHashCodeAs(after);
  }

  @Test
  void treats_states_the_database_has_not_identified_as_distinct() {
    assertThat(new LicitacionState(4, "Adxudicado"))
        .isNotEqualTo(new LicitacionState(4, "Adxudicado"));
  }
}
