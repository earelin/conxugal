package gal.conxugal.infrastructure.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SystemClockTest {

  private final SystemClock clock = new SystemClock();

  @Test
  void reads_value_close_to_the_current_instant() {
    assertThat(clock.instant()).isCloseTo(Instant.now(), within(Duration.ofSeconds(1)));
  }

  @Test
  void never_goes_backwards_between_successive_calls() {
    Instant first = clock.instant();

    Instant second = clock.instant();

    assertThat(second).isAfterOrEqualTo(first);
  }
}
