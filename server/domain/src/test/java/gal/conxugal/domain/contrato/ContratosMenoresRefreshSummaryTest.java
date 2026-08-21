package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ContratosMenoresRefreshSummaryTest {

  /**
   * The absent reason is what the walk reads to decide whether it may move the refresh mark, so a
   * {@code stopped} summary carrying none would be indistinguishable from a clean one — and the
   * mark would jump over a period nothing read.
   */
  @Test
  void rejects_the_stopped_refresh_that_names_no_reason() {
    assertThatNullPointerException()
        .isThrownBy(() -> ContratosMenoresRefreshSummary.stopped(0, 0, null))
        .withMessageContaining("stoppedBy");
  }

  @Test
  void clean_refresh_leaves_nothing_to_explain() {
    assertThat(ContratosMenoresRefreshSummary.clean(3, 7))
        .isEqualTo(new ContratosMenoresRefreshSummary(3, 7, null));
  }
}
