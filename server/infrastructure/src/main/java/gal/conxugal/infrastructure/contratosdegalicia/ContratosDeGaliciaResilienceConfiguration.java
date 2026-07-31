package gal.conxugal.infrastructure.contratosdegalicia;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.Duration;

/**
 * How hard conxugal is willing to lean on contratosdegalicia.gal. Policy settings only — the base
 * URL and the connect and read timeouts belong to the client itself and bind under {@code
 * micronaut.http.services.contratosdegalicia}.
 *
 * <p>Defaults are deliberately at the ceiling ADR-0014 fixes: one request in flight, no faster than
 * one per second. The source publishes no limits, so every number here is an educated guess made
 * on the conservative side.
 */
@ConfigurationProperties(ContratosDeGaliciaResilienceConfiguration.PREFIX)
public record ContratosDeGaliciaResilienceConfiguration(
    @Bindable(defaultValue = "1s") Duration rateLimitRefreshPeriod,
    @Bindable(defaultValue = "10s") Duration maximumPermitWait,
    @Bindable(defaultValue = "1") int maxConcurrentCalls,
    @Bindable(defaultValue = "3") int retryMaxAttempts,
    @Bindable(defaultValue = "1s") Duration retryBaseDelay,
    @Bindable(defaultValue = "60s") Duration retryAfterMaximumWait,
    @Bindable(defaultValue = "50") int breakerFailureRateThreshold,
    @Bindable(defaultValue = "5m") Duration breakerOpenStateDuration) {

  static final String PREFIX = "conxugal.contratosdegalicia.resilience";

  /**
   * Resilience4j's own floor: {@code IntervalFunction} and the policy configs reject anything below
   * one millisecond, from inside the first outbound call rather than at binding time.
   */
  private static final Duration MINIMUM_DURATION = Duration.ofMillis(1);

  public ContratosDeGaliciaResilienceConfiguration {
    requireAtLeast(rateLimitRefreshPeriod, MINIMUM_DURATION, "rate-limit-refresh-period");
    requireNotNegative(maximumPermitWait, "maximum-permit-wait");
    requireAtLeast(maxConcurrentCalls, 1, "max-concurrent-calls");
    requireAtLeast(retryMaxAttempts, 1, "retry-max-attempts");
    requireAtLeast(retryBaseDelay, MINIMUM_DURATION, "retry-base-delay");
    requireNotNegative(retryAfterMaximumWait, "retry-after-maximum-wait");
    requireInRange(breakerFailureRateThreshold, 1, 100, "breaker-failure-rate-threshold");
    requireAtLeast(breakerOpenStateDuration, MINIMUM_DURATION, "breaker-open-state-duration");
    requirePermitWaitCoversConcurrency(
        rateLimitRefreshPeriod, maximumPermitWait, maxConcurrentCalls);
  }

  /**
   * {@code AtomicRateLimiter} does not queue indefinitely: it computes the wait a caller needs
   * given reservations already in flight and refuses outright if that exceeds the maximum. So the
   * permit wait fixes a ceiling on concurrent callers, and a combination violating it does not fail
   * here — it fails much later, as refused permits under load, looking like a source problem rather
   * than a configuration one.
   */
  private static void requirePermitWaitCoversConcurrency(
      Duration refreshPeriod, Duration maximumPermitWait, int maxConcurrentCalls) {
    Duration required = refreshPeriod.multipliedBy(maxConcurrentCalls);
    if (maximumPermitWait.compareTo(required) < 0) {
      throw new IllegalArgumentException(
          ("maximum-permit-wait must be at least rate-limit-refresh-period × max-concurrent-calls "
                  + "(%s × %d = %s), was %s: %d concurrent callers would see permits refused "
                  + "instead of paced")
              .formatted(
                  refreshPeriod, maxConcurrentCalls, required, maximumPermitWait,
                  maxConcurrentCalls));
    }
  }

  private static void requireAtLeast(Duration value, Duration minimum, String name) {
    if (value == null || value.compareTo(minimum) < 0) {
      throw new IllegalArgumentException(
          "%s must be at least %s, was %s".formatted(name, minimum, value));
    }
  }

  private static void requireAtLeast(int value, int minimum, String name) {
    if (value < minimum) {
      throw new IllegalArgumentException(
          "%s must be at least %d, was %d".formatted(name, minimum, value));
    }
  }

  private static void requireNotNegative(Duration value, String name) {
    if (value == null || value.isNegative()) {
      throw new IllegalArgumentException("%s must not be negative, was %s".formatted(name, value));
    }
  }

  private static void requireInRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          "%s must be between %d and %d, was %d".formatted(name, minimum, maximum, value));
    }
  }
}
