package gal.conxugal.infrastructure.http;

import java.time.Duration;

/**
 * {@link ResilientHttpClientSettings} for tests, built through {@link #builder()} so a scenario
 * names only the knobs it actually cares about. The defaults are fast enough for a unit test and
 * still satisfy the inequality the real per-source records must hold — {@code maxConcurrentCalls
 * × rateLimiterRefreshPeriod <= maximumPermitWait}.
 *
 * <p>Production settings are bound per source by that source's own {@code @ConfigurationProperties}
 * record; nothing in {@code main} implements this interface.
 */
public record TestResilientHttpClientSettings(
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

  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder seeded with defaults; {@link #build()} produces the immutable record. */
  public static final class Builder {

    private String baseUrl = "https://example.test";
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(1);
    private Duration rateLimiterRefreshPeriod = Duration.ofMillis(10);
    private Duration maximumPermitWait = Duration.ofSeconds(5);
    private int maxConcurrentCalls = 1;
    private int retryMaxAttempts = 3;
    private Duration retryBaseDelay = Duration.ofMillis(5);
    private Duration retryAfterMaximumWait = Duration.ofSeconds(5);
    private int breakerFailureRateThreshold = 50;
    private Duration breakerOpenStateDuration = Duration.ofSeconds(5);

    private Builder() {}

    public Builder baseUrl(String value) {
      this.baseUrl = value;
      return this;
    }

    public Builder connectTimeout(Duration value) {
      this.connectTimeout = value;
      return this;
    }

    public Builder readTimeout(Duration value) {
      this.readTimeout = value;
      return this;
    }

    public Builder rateLimiterRefreshPeriod(Duration value) {
      this.rateLimiterRefreshPeriod = value;
      return this;
    }

    public Builder maximumPermitWait(Duration value) {
      this.maximumPermitWait = value;
      return this;
    }

    public Builder maxConcurrentCalls(int value) {
      this.maxConcurrentCalls = value;
      return this;
    }

    public Builder retryMaxAttempts(int value) {
      this.retryMaxAttempts = value;
      return this;
    }

    public Builder retryBaseDelay(Duration value) {
      this.retryBaseDelay = value;
      return this;
    }

    public Builder retryAfterMaximumWait(Duration value) {
      this.retryAfterMaximumWait = value;
      return this;
    }

    public Builder breakerFailureRateThreshold(int value) {
      this.breakerFailureRateThreshold = value;
      return this;
    }

    public Builder breakerOpenStateDuration(Duration value) {
      this.breakerOpenStateDuration = value;
      return this;
    }

    public TestResilientHttpClientSettings build() {
      return new TestResilientHttpClientSettings(
          baseUrl,
          connectTimeout,
          readTimeout,
          rateLimiterRefreshPeriod,
          maximumPermitWait,
          maxConcurrentCalls,
          retryMaxAttempts,
          retryBaseDelay,
          retryAfterMaximumWait,
          breakerFailureRateThreshold,
          breakerOpenStateDuration);
    }
  }
}
