package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ImportRunStateTest {

  private static final Instant LAST_ADVANCED_AT = Instant.parse("2026-08-07T09:00:00Z");
  private static final Duration BOUND = Duration.ofMinutes(15);

  @Test
  void reads_run_that_has_not_advanced_within_the_bound_as_abandoned() {
    Instant now = LAST_ADVANCED_AT.plus(BOUND).plusSeconds(1);

    assertThat(ImportRunState.IN_PROGRESS.asReadAt(LAST_ADVANCED_AT, now, BOUND))
        .isEqualTo(ImportRunState.ABANDONED);
  }

  @Test
  void reads_run_that_advanced_within_the_bound_as_still_in_progress() {
    Instant now = LAST_ADVANCED_AT.plus(BOUND).minusSeconds(1);

    assertThat(ImportRunState.IN_PROGRESS.asReadAt(LAST_ADVANCED_AT, now, BOUND))
        .isEqualTo(ImportRunState.IN_PROGRESS);
  }

  @Test
  void reads_run_that_advanced_exactly_at_the_bound_as_still_in_progress() {
    // The bound is how long a live run may go quiet, not the first moment it is doubted.
    Instant now = LAST_ADVANCED_AT.plus(BOUND);

    assertThat(ImportRunState.IN_PROGRESS.asReadAt(LAST_ADVANCED_AT, now, BOUND))
        .isEqualTo(ImportRunState.IN_PROGRESS);
  }

  @Test
  void leaves_run_that_already_finished_as_it_was_however_long_ago() {
    Instant now = LAST_ADVANCED_AT.plus(Duration.ofDays(365));

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(ImportRunState.SUCCEEDED.asReadAt(LAST_ADVANCED_AT, now, BOUND))
          .isEqualTo(ImportRunState.SUCCEEDED);
      softly.assertThat(ImportRunState.PARTIALLY_SUCCEEDED.asReadAt(LAST_ADVANCED_AT, now, BOUND))
          .isEqualTo(ImportRunState.PARTIALLY_SUCCEEDED);
      softly.assertThat(ImportRunState.FAILED.asReadAt(LAST_ADVANCED_AT, now, BOUND))
          .isEqualTo(ImportRunState.FAILED);
    });
  }

  @Test
  void every_verdict_of_the_four_can_complete_run() {
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(ImportRunState.SUCCEEDED.requireStorableVerdict())
          .isEqualTo(ImportRunState.SUCCEEDED);
      softly.assertThat(ImportRunState.PARTIALLY_SUCCEEDED.requireStorableVerdict())
          .isEqualTo(ImportRunState.PARTIALLY_SUCCEEDED);
      softly.assertThat(ImportRunState.FAILED.requireStorableVerdict())
          .isEqualTo(ImportRunState.FAILED);
    });
  }

  // Storing it would freeze the run in a state no resumption can tell from a live one, and the
  // value reaches a writer easily: a caller completing with the state it just read has one.
  @Test
  void refuses_to_complete_run_as_abandoned() {
    assertThatIllegalArgumentException()
        .isThrownBy(ImportRunState.ABANDONED::requireStorableVerdict)
        .withMessageContaining("ABANDONED");
  }

  @Test
  void refuses_to_complete_run_as_still_in_progress() {
    assertThatIllegalArgumentException()
        .isThrownBy(ImportRunState.IN_PROGRESS::requireStorableVerdict);
  }
}
