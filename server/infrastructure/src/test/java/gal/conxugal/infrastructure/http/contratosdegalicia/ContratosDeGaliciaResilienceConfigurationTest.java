package gal.conxugal.infrastructure.http.contratosdegalicia;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContratosDeGaliciaResilienceConfigurationTest {

  private static final Duration ONE_SECOND = Duration.ofSeconds(1);
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);
  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  @Test
  void accepts_the_conservative_defaults() {
    assertThatCode(() -> configuration(ONE_SECOND, TEN_SECONDS, 1)).doesNotThrowAnyException();
  }

  @Test
  void accepts_concurrency_the_permit_wait_can_absorb() {
    assertThatCode(() -> configuration(ONE_SECOND, TEN_SECONDS, 10)).doesNotThrowAnyException();
  }

  /**
   * The combination this rejects is the one ADR-0014 warns about: it fails nowhere at binding time
   * and then surfaces much later as refused permits under load, reading as a source problem rather
   * than a configuration one.
   */
  @Test
  void rejects_concurrency_beyond_what_the_permit_wait_can_absorb() {
    assertThatThrownBy(() -> configuration(ONE_SECOND, TEN_SECONDS, 11))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximum-permit-wait must be at least");
  }

  @Test
  void rejects_negative_permit_wait() {
    assertThatThrownBy(() -> configuration(ONE_SECOND, Duration.ofSeconds(-1), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximum-permit-wait must not be negative");
  }

  @Test
  void rejects_zero_refresh_period() {
    assertThatThrownBy(() -> configuration(Duration.ZERO, TEN_SECONDS, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rate-limit-refresh-period must be at least");
  }

  @Test
  void rejects_failure_rate_threshold_outside_the_percentage_range() {
    assertThatThrownBy(
            () ->
                new ContratosDeGaliciaResilienceConfiguration(
                    ONE_SECOND, TEN_SECONDS, 1, 3, ONE_SECOND, ONE_MINUTE, 101, ONE_MINUTE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("breaker-failure-rate-threshold must be between 1 and 100");
  }

  @Test
  void rejects_fewer_than_one_retry_attempt() {
    assertThatThrownBy(
            () ->
                new ContratosDeGaliciaResilienceConfiguration(
                    ONE_SECOND, TEN_SECONDS, 1, 0, ONE_SECOND, ONE_MINUTE, 50, ONE_MINUTE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retry-max-attempts must be at least 1");
  }

  private static ContratosDeGaliciaResilienceConfiguration configuration(
      Duration refreshPeriod, Duration maximumPermitWait, int maxConcurrentCalls) {
    return new ContratosDeGaliciaResilienceConfiguration(
        refreshPeriod,
        maximumPermitWait,
        maxConcurrentCalls,
        3,
        ONE_SECOND,
        ONE_MINUTE,
        50,
        ONE_MINUTE);
  }
}
