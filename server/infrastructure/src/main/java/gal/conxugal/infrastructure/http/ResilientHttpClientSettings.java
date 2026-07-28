package gal.conxugal.infrastructure.http;

import java.time.Duration;

/**
 * Per-source configuration for a {@link ResilientHttpClient}. A source's own {@code
 * @ConfigurationProperties} record implements this interface directly — records may implement
 * interfaces, which sidesteps {@code @EachProperty}/{@code @EachBean} — e.g.:
 *
 * <pre>{@code
 * @ConfigurationProperties("conxugal.contratosdegalicia.http")
 * public record ContratosDeGaliciaHttpSettings(
 *     String baseUrl,
 *     @Bindable(defaultValue = "5s") Duration connectTimeout,
 *     @Bindable(defaultValue = "10s") Duration readTimeout,
 *     ...) implements ResilientHttpClientSettings {}
 * }</pre>
 */
public interface ResilientHttpClientSettings {

  String baseUrl();

  Duration connectTimeout();

  Duration readTimeout();

  /**
   * Rate-limiter refresh period. {@link ResilientHttpClient} fixes {@code limitForPeriod} at 1, so
   * this alone sets the pace: one permit granted per period.
   */
  Duration rateLimiterRefreshPeriod();

  /** Maximum time a caller waits for a permit before failing with a translated refusal. */
  Duration maximumPermitWait();

  /**
   * The number of callers expected in flight concurrently. Not enforced by any bulkhead; it exists
   * so a reviewer can check {@code maxConcurrentCalls * rateLimiterRefreshPeriod <=
   * maximumPermitWait} (limitForPeriod is fixed at 1) — nothing validates this inequality at
   * runtime.
   */
  int maxConcurrentCalls();

  int retryMaxAttempts();

  /** Base delay for exponential-with-jitter backoff, absent a {@code Retry-After} header. */
  Duration retryBaseDelay();

  /** Clamp on a parsed {@code Retry-After}; exceeding it aborts the call instead of waiting. */
  Duration retryAfterMaximumWait();

  /** Percentage, 0-100, of calls in the sliding window that must fail to open the circuit. */
  int breakerFailureRateThreshold();

  Duration breakerOpenStateDuration();
}
