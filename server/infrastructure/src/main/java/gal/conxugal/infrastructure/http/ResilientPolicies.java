package gal.conxugal.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/** Builds the Resilience4j policy configs {@link ResilientHttpClient} composes. */
final class ResilientPolicies {

  static final int BREAKER_SLIDING_WINDOW_SIZE = 20;
  static final int BREAKER_MINIMUM_NUMBER_OF_CALLS = 5;

  private static final Set<Integer> RETRYABLE_STATUSES =
      Set.of(408, 425, 429, 500, 502, 503, 504);
  private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
  private static final double RETRY_BACKOFF_RANDOMIZATION_FACTOR = 0.5;

  private ResilientPolicies() {}

  static RateLimiterConfig rateLimiterConfig(ResilientHttpClientSettings settings) {
    return RateLimiterConfig.custom()
        .limitForPeriod(1)
        .limitRefreshPeriod(settings.rateLimiterRefreshPeriod())
        .timeoutDuration(settings.maximumPermitWait())
        .build();
  }

  static CircuitBreakerConfig circuitBreakerConfig(ResilientHttpClientSettings settings) {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(settings.breakerFailureRateThreshold())
        .waitDurationInOpenState(settings.breakerOpenStateDuration())
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(BREAKER_SLIDING_WINDOW_SIZE)
        .minimumNumberOfCalls(BREAKER_MINIMUM_NUMBER_OF_CALLS)
        // Failure-rate detection only: the limiter wait this breaker encloses would otherwise
        // trip slow-call detection as a false symptom of source health.
        .slowCallDurationThreshold(Duration.ofHours(1))
        .ignoreException(throwable -> throwable instanceof RequestNotPermitted)
        .build();
  }

  static RetryConfig retryConfig(ResilientHttpClientSettings settings, Clock clock) {
    IntervalFunction jitteredBackoff =
        IntervalFunction.ofExponentialRandomBackoff(
            settings.retryBaseDelay(),
            RETRY_BACKOFF_MULTIPLIER,
            RETRY_BACKOFF_RANDOMIZATION_FACTOR);
    Duration retryAfterMaximumWait = settings.retryAfterMaximumWait();
    return RetryConfig.<Object>custom()
        .maxAttempts(settings.retryMaxAttempts())
        .retryOnException(ResilientPolicies::isRetryableFailure)
        .intervalBiFunction(
            (attempt, outcome) ->
                computeWaitMillis(attempt, outcome, retryAfterMaximumWait, clock, jitteredBackoff))
        .build();
  }

  private static long computeWaitMillis(
      int attempt,
      Either<Throwable, Object> outcome,
      Duration retryAfterMaximumWait,
      Clock clock,
      IntervalFunction jitteredBackoff) {
    if (outcome.isLeft()
        && outcome.getLeft() instanceof HttpClientResponseException responseException) {
      return RetryAfter.parse(responseException.getResponse(), clock)
          .map(wait -> waitOrAbort(wait, retryAfterMaximumWait))
          .orElseGet(() -> jitteredBackoff.apply(attempt));
    }
    return jitteredBackoff.apply(attempt);
  }

  private static long waitOrAbort(Duration wait, Duration maximumWait) {
    if (wait.compareTo(maximumWait) > 0) {
      throw new RetryAfterExceedsMaximumWaitException(
          "Retry-After %s exceeds the configured maximum wait %s".formatted(wait, maximumWait));
    }
    return wait.toMillis();
  }

  private static boolean isRetryableFailure(Throwable throwable) {
    if (throwable instanceof HttpClientResponseException responseException) {
      return RETRYABLE_STATUSES.contains(responseException.getStatus().getCode());
    }
    if (throwable instanceof UnacceptableResponseException
        || throwable instanceof RequestNotPermitted
        || throwable instanceof CallNotPermittedException
        || throwable instanceof RetryAfterExceedsMaximumWaitException) {
      return false;
    }
    return throwable instanceof HttpClientException || throwable instanceof IOException;
  }
}
