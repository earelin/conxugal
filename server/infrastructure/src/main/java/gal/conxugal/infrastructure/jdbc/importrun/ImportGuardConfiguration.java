package gal.conxugal.infrastructure.jdbc.importrun;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.Duration;

/**
 * How long a run may go quiet before the single-import guard stops believing in it. Distinct from
 * {@code conxugal.organos.import}, which schedules one importer: this is system-wide, and both
 * importers are held by it.
 *
 * <p>Fifteen minutes because the longest legitimate gap between two advances is one slice's worth
 * of the shipped outbound settings — up to ten seconds waiting for a rate-limit permit, then three
 * attempts at a ten-second read timeout with backoff, roughly a minute at worst. That leaves an
 * order of magnitude of headroom while still releasing the guard well inside the time an
 * administrator would wait on a crashed run, and nothing clears one by hand.
 *
 * <p>The batch a run advances after is <em>not</em> configurable and is not here: it is one source
 * page, which the source itself caps at a hundred rows. Tying the two together is what keeps the
 * bound meaningful — a batch coarser than the bound would make a healthy run read as dead.
 */
@ConfigurationProperties(ImportGuardConfiguration.PREFIX)
public record ImportGuardConfiguration(
    @Bindable(defaultValue = "15m") Duration abandonmentBound) {

  static final String PREFIX = "conxugal.import";

  public ImportGuardConfiguration {
    if (abandonmentBound == null || abandonmentBound.isZero() || abandonmentBound.isNegative()) {
      // A non-positive bound reads every run as abandoned the instant it is claimed, which is not
      // a lenient guard but no guard at all — and it fails silently, as two imports at once.
      throw new IllegalArgumentException(
          "abandonment-bound must be positive, was %s".formatted(abandonmentBound));
    }
  }
}
