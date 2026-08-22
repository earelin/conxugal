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
        .isThrownBy(() -> new UpsertOutcome(null, UpsertOperation.ADDED));
  }

  @Test
  void rejects_an_outcome_naming_no_operation() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UpsertOutcome(new LicitacionId(UUID.randomUUID()), null));
  }

  @Test
  void reports_the_identity_the_row_was_stored_under_and_which_branch_it_took() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());

    UpsertOutcome outcome = new UpsertOutcome(id, UpsertOperation.REFRESHED);

    assertThat(outcome.id()).isEqualTo(id);
    assertThat(outcome.operation()).isEqualTo(UpsertOperation.REFRESHED);
  }

  @Test
  void separates_the_procedure_the_store_did_not_hold_from_the_one_it_refreshed() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());

    assertThat(new UpsertOutcome(id, UpsertOperation.ADDED))
        .isNotEqualTo(new UpsertOutcome(id, UpsertOperation.REFRESHED));
  }
}
