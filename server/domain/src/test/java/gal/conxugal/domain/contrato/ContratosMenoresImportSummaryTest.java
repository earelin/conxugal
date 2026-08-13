package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import org.junit.jupiter.api.Test;

class ContratosMenoresImportSummaryTest {

  @Test
  void rejects_null_status() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ContratosMenoresImportSummary(0, 0, null, null))
        .withMessageContaining("status");
  }

  /** The javadoc's claim, made executable: a walk that ran has started by definition. */
  @Test
  void rejects_the_walk_that_reads_as_never_started() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContratosMenoresImportSummary(
                    0, 0, ContratosMenoresImportStatus.NEVER_STARTED, null));
  }

  /**
   * The pair is the point: a stopped walk that read as complete would leave the Órgano marked
   * loaded on a history it never finished, and nothing would ever resume it.
   */
  @Test
  void rejects_the_stopped_walk_that_reads_as_complete() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContratosMenoresImportSummary(
                    0,
                    0,
                    ContratosMenoresImportStatus.COMPLETE,
                    ContratosMenoresImportSummary.StopReason.UNMARKED));
  }

  @Test
  void leaves_the_stopped_walk_incomplete() {
    ContratosMenoresImportSummary summary =
        ContratosMenoresImportSummary.stopped(
            100, 3, ContratosMenoresImportSummary.StopReason.GUARD_LOST);

    assertThat(summary.status()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
    assertThat(summary.stoppedBy())
        .isEqualTo(ContratosMenoresImportSummary.StopReason.GUARD_LOST);
  }

  /** Incomplete is not stopped: a walk that reached the history floor ended on its own terms. */
  @Test
  void does_not_report_the_incomplete_walk_as_stopped() {
    assertThat(ContratosMenoresImportSummary.incomplete(100, 3).stoppedBy()).isNull();
  }

  @Test
  void does_not_report_the_complete_walk_as_stopped() {
    assertThat(ContratosMenoresImportSummary.complete(100, 3).stoppedBy()).isNull();
  }
}
