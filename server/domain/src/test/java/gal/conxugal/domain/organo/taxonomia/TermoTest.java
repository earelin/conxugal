package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TermoTest {

  @Test
  void constructs_root_term_with_null_parent() {
    Termo root = new Termo(new TermoId(UUID.randomUUID()), "Deportes", null);

    assertThat(root.parentId()).isNull();
  }

  @Test
  void constructs_child_term_under_parent() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    Termo child = new Termo(new TermoId(UUID.randomUUID()), "Fútbol", parentId);

    assertThat(child.parentId()).isEqualTo(parentId);
  }

  @Test
  void allows_null_id_before_being_persisted() {
    Termo term = new Termo(null, "Deportes", null);

    assertThat(term.id()).isNull();
  }

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Termo(new TermoId(UUID.randomUUID()), null, null));
  }

  @Test
  void newly_created_term_has_no_id_and_the_given_parent() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    Termo term = new Termo("Fútbol", parentId);

    assertThat(term.id()).isNull();
    assertThat(term.parentId()).isEqualTo(parentId);
  }

  @Test
  void nesting_is_unbounded() {
    Termo root = new Termo(new TermoId(UUID.randomUUID()), "Nivel 1", null);
    Termo child = new Termo(new TermoId(UUID.randomUUID()), "Nivel 2", root.id());
    Termo grandchild = new Termo(new TermoId(UUID.randomUUID()), "Nivel 3", child.id());
    Termo greatGrandchild = new Termo(new TermoId(UUID.randomUUID()), "Nivel 4", grandchild.id());

    assertThat(greatGrandchild.parentId()).isEqualTo(grandchild.id());
  }

  @Test
  void renaming_and_moving_leaves_the_same_term() {
    TermoId termoId = new TermoId(UUID.randomUUID());
    Termo before = new Termo(termoId, "Fútbol", null);
    Termo after = new Termo(termoId, "Fútbol Sala", new TermoId(UUID.randomUUID()));

    assertThat(before).isEqualTo(after);
    assertThat(before).hasSameHashCodeAs(after);
  }

  @Test
  void terms_under_different_ids_are_different_terms() {
    Termo one = new Termo(new TermoId(UUID.randomUUID()), "Deportes", null);
    Termo other = new Termo(new TermoId(UUID.randomUUID()), "Deportes", null);

    assertThat(one).isNotEqualTo(other);
  }

  @Test
  void unpersisted_terms_are_equal_to_nothing_but_themselves() {
    Termo one = new Termo("Deportes", null);
    Termo other = new Termo("Deportes", null);

    assertThat(one).isNotEqualTo(other);
    assertThat(Set.of(one, other))
        .hasSize(2)
        .contains(one, other);
  }

  @Test
  void an_unpersisted_term_matches_no_persisted_one_in_either_direction() {
    Termo unpersisted = new Termo("Deportes", null);
    Termo persisted = new Termo(new TermoId(UUID.randomUUID()), "Deportes", null);

    assertThat(unpersisted).isNotEqualTo(persisted);
    assertThat(persisted).isNotEqualTo(unpersisted);
  }
}
