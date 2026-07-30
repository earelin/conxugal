package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TermoTest {

  @Test
  void constructs_root_term_with_null_parent() {
    Termo root = new Termo(UUID.randomUUID(), "Deportes", null);

    assertThat(root.parentId()).isNull();
  }

  @Test
  void constructs_child_term_under_parent() {
    UUID parentId = UUID.randomUUID();
    Termo child = new Termo(UUID.randomUUID(), "Fútbol", parentId);

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
        .isThrownBy(() -> new Termo(UUID.randomUUID(), null, null));
  }

  @Test
  void newly_created_term_has_no_id_and_the_given_parent() {
    UUID parentId = UUID.randomUUID();
    Termo term = new Termo("Fútbol", parentId);

    assertThat(term.id()).isNull();
    assertThat(term.parentId()).isEqualTo(parentId);
  }

  @Test
  void nesting_is_unbounded() {
    Termo root = new Termo(UUID.randomUUID(), "Nivel 1", null);
    Termo child = new Termo(UUID.randomUUID(), "Nivel 2", root.id());
    Termo grandchild = new Termo(UUID.randomUUID(), "Nivel 3", child.id());
    Termo greatGrandchild = new Termo(UUID.randomUUID(), "Nivel 4", grandchild.id());

    assertThat(greatGrandchild.parentId()).isEqualTo(grandchild.id());
  }
}
