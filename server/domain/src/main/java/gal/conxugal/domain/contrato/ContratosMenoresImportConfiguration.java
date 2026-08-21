package gal.conxugal.domain.contrato;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.Duration;
import java.time.LocalDate;

/**
 * The two numbers a contratos menores walk is bounded by: how far back an initial import may go
 * before it gives up, and how far behind its own refresh mark an incremental one starts.
 *
 * <p>An initial walk ends when an Órgano's stored count reaches the count the source reports; the
 * history floor is the backstop for the case where the two never converge, so a walk cannot step
 * backwards forever. 2018 because that is where the source's published history begins. Reaching the
 * floor without matching the count leaves the Órgano <em>incomplete</em> rather than silently
 * complete, so it is resumed later rather than treated as loaded — which is why an approximate
 * floor is safe: setting it too early costs empty windows, and setting it too late is visible as an
 * import that never completes.
 *
 * <p>The lookback is the margin an incremental refresh subtracts from its own floor. The source
 * offers no <em>changed since</em> facility, so a correction is discoverable only by re-reading the
 * period it falls in; nothing has measured how long after publication the source rectifies an
 * entry, and 30 days is chosen to be comfortably longer than a plausible administrative correction
 * cycle. Its cost is the last month of every marked Órgano re-read every night, for ever.
 *
 * <p><strong>What is here and what is not follows one rule.</strong> The source's own
 * <em>measured limits</em> — the three-month window and the hundred-row page — are not
 * configurable, because they are facts about the source rather than guesses, and the page is also
 * the batch a run advances after, so tying it to configuration would make the abandonment bound
 * negotiable against a figure the source decides. The <em>educated guesses about it</em> — both
 * numbers below — are, since they are the ones a measurement would move.
 */
@ConfigurationProperties(ContratosMenoresImportConfiguration.PREFIX)
public record ContratosMenoresImportConfiguration(
    @Bindable(defaultValue = "2018-01-01") LocalDate historyFloor,
    @Bindable(defaultValue = "30d") Duration lookback) {

  static final String PREFIX = "conxugal.contratos-menores.import";

  public ContratosMenoresImportConfiguration {
    if (historyFloor == null) {
      // Without a floor the walk has no backstop at all: an Órgano whose count never converges
      // would step backwards a quarter at a time for as long as the process lived.
      throw new IllegalArgumentException("history-floor must be set");
    }
    requirePositive(lookback);
  }

  /**
   * A margin that is not a margin at all is refused where the property binds, so a typo in a config
   * file fails the application at startup rather than at the first nightly sweep.
   *
   * <p>The refresh floor subtracts whatever it is given. A negative lookback therefore moves that
   * floor <em>ahead</em> of the instant the refresh stamps, and everything published in between
   * falls below every future floor — reachable by nothing, and silent. Zero is refused with it: the
   * margin exists because corrections arrive after publication, and one of zero width finds none of
   * them.
   */
  private static void requirePositive(Duration lookback) {
    if (lookback == null || lookback.isNegative() || lookback.isZero()) {
      throw new IllegalArgumentException("lookback must be positive, was %s".formatted(lookback));
    }
  }
}
