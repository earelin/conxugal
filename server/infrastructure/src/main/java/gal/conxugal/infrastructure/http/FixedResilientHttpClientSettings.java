package gal.conxugal.infrastructure.http;

import java.time.Duration;

/** Plain {@link ResilientHttpClientSettings}, for tests and callers outside Micronaut binding. */
public record FixedResilientHttpClientSettings(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration rateLimiterRefreshPeriod,
    Duration maximumPermitWait,
    int maxConcurrentCalls,
    int retryMaxAttempts,
    Duration retryBaseDelay,
    Duration retryAfterMaximumWait,
    int breakerFailureRateThreshold,
    Duration breakerOpenStateDuration)
    implements ResilientHttpClientSettings {

  /**
   * The ceiling this package's design fixes: at most one request in flight per source, no faster
   * than one per second. {@code maxConcurrentCalls(1) * rateLimiterRefreshPeriod(1s) = 1s <=
   * maximumPermitWait(2s)}, with a second of slack.
   */
  public static ResilientHttpClientSettings defaults(String baseUrl) {
    return new FixedResilientHttpClientSettings(
        baseUrl,
        Duration.ofSeconds(5),
        Duration.ofSeconds(10),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        1,
        3,
        Duration.ofMillis(500),
        Duration.ofSeconds(30),
        50,
        Duration.ofSeconds(30));
  }
}
