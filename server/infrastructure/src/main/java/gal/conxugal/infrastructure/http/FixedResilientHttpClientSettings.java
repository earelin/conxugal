package gal.conxugal.infrastructure.http;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.Duration;

/**
 * {@link ResilientHttpClientSettings} bound from configuration under {@link #PREFIX}. Records may
 * implement interfaces and still bind normally, so this doubles as the settings tests construct
 * directly wherever a scenario needs its own tuning (fast timeouts, a low breaker threshold, …).
 *
 * <p>The {@code @Bindable} defaults are the ceiling this package's design fixes: at most one
 * request in flight per source, no faster than one per second — {@code
 * maxConcurrentCalls(1) * rateLimiterRefreshPeriod(1s) = 1s <= maximumPermitWait(2s)}, with a
 * second of slack.
 */
@ConfigurationProperties(FixedResilientHttpClientSettings.PREFIX)
public record FixedResilientHttpClientSettings(
    String baseUrl,
    @Bindable(defaultValue = "5s") Duration connectTimeout,
    @Bindable(defaultValue = "10s") Duration readTimeout,
    @Bindable(defaultValue = "1s") Duration rateLimiterRefreshPeriod,
    @Bindable(defaultValue = "2s") Duration maximumPermitWait,
    @Bindable(defaultValue = "1") int maxConcurrentCalls,
    @Bindable(defaultValue = "3") int retryMaxAttempts,
    @Bindable(defaultValue = "500ms") Duration retryBaseDelay,
    @Bindable(defaultValue = "30s") Duration retryAfterMaximumWait,
    @Bindable(defaultValue = "50") int breakerFailureRateThreshold,
    @Bindable(defaultValue = "30s") Duration breakerOpenStateDuration)
    implements ResilientHttpClientSettings {

  public static final String PREFIX = "conxugal.http.resilient-client";
}
