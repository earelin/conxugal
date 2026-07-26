package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ImportOutcomeTest {

  @Test
  void success_carries_the_added_refreshed_and_deactivated_counts() {
    ImportOutcome outcome = ImportOutcome.success(2, 3, 1);

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(outcome.added()).isEqualTo(2);
    assertThat(outcome.refreshed()).isEqualTo(3);
    assertThat(outcome.deactivated()).isEqualTo(1);
  }

  @Test
  void failure_reports_no_counts() {
    ImportOutcome outcome = ImportOutcome.failure();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    assertThat(outcome.added()).isZero();
    assertThat(outcome.refreshed()).isZero();
    assertThat(outcome.deactivated()).isZero();
  }

  @Test
  void already_running_reports_no_counts() {
    ImportOutcome outcome = ImportOutcome.alreadyRunning();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(outcome.added()).isZero();
    assertThat(outcome.refreshed()).isZero();
    assertThat(outcome.deactivated()).isZero();
  }

  @Test
  void rejects_null_status() {
    assertThatNullPointerException().isThrownBy(() -> new ImportOutcome(null, 0, 0, 0));
  }
}
