package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpsertOutcomeTest {

  @Test
  void rejects_an_outcome_naming_no_identity() {
    // The identity is what the procedure's children are attached to, so a null one surfaces as a
    // failed write long after the adapter that answered it.
    assertThatNullPointerException()
        .isThrownBy(() -> new UpsertOutcome(null, true));
  }

  @Test
  void reports_the_identity_the_row_was_stored_under_and_which_branch_it_took() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());

    UpsertOutcome outcome = new UpsertOutcome(id, false);

    assertThat(outcome.id()).isEqualTo(id);
    assertThat(outcome.added()).isFalse();
  }
}
