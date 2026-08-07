package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ImportGuardConfigurationTest {

  @Test
  void keeps_the_bound_it_is_configured_with() {
    Duration bound = Duration.ofMinutes(30);

    assertThat(new ImportGuardConfiguration(bound).abandonmentBound()).isEqualTo(bound);
  }

  // Not a lenient guard but no guard at all: every run would read as abandoned the instant it was
  // claimed, and the failure would show up as two imports at once rather than as an error here.
  @Test
  void rejects_bound_of_zero() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ImportGuardConfiguration(Duration.ZERO))
        .withMessageContaining("abandonment-bound must be positive");
  }

  @Test
  void rejects_negative_bound() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ImportGuardConfiguration(Duration.ofMinutes(-1)));
  }

  @Test
  void rejects_missing_bound() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ImportGuardConfiguration(null));
  }
}
