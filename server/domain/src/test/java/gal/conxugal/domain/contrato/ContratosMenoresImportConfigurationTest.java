package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContratosMenoresImportConfigurationTest {

  private static final LocalDate HISTORY_FLOOR = LocalDate.of(2018, 1, 1);
  private static final Duration LOOKBACK = Duration.ofDays(30);

  @Test
  void keeps_the_two_bounds_it_is_configured_with() {
    ContratosMenoresImportConfiguration configuration =
        new ContratosMenoresImportConfiguration(HISTORY_FLOOR, LOOKBACK);

    assertThat(configuration.historyFloor()).isEqualTo(HISTORY_FLOOR);
    assertThat(configuration.lookback()).isEqualTo(LOOKBACK);
  }

  // Without a floor the walk has no backstop at all: an Órgano whose count never converges would
  // step backwards a quarter at a time for as long as the process lived.
  @Test
  void refuses_missing_history_floor() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContratosMenoresImportConfiguration(null, LOOKBACK))
        .withMessageContaining("history-floor must be set");
  }

  /**
   * The failure this refusal exists to prevent is silent and permanent. The refresh floor subtracts
   * whatever it is given, so a negative margin moves that floor <em>ahead</em> of the instant the
   * refresh stamps, and everything published in between falls below every future floor. Refused
   * where the property binds, so it fails at startup rather than at the first nightly sweep.
   */
  @Test
  void refuses_negative_lookback() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ContratosMenoresImportConfiguration(HISTORY_FLOOR, Duration.ofDays(-1)))
        .withMessageContaining("lookback must be positive");
  }

  // The margin exists because corrections arrive after publication; one of zero width finds none.
  @Test
  void refuses_lookback_of_zero() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContratosMenoresImportConfiguration(HISTORY_FLOOR, Duration.ZERO))
        .withMessageContaining("lookback must be positive");
  }

  @Test
  void refuses_missing_lookback() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContratosMenoresImportConfiguration(HISTORY_FLOOR, null));
  }
}
