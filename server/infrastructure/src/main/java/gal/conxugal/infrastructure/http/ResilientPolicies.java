package gal.conxugal.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.exception.AcquirePermissionCancelledException;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
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
        // Our own throttling, not a source-health signal: neither counts toward the failure rate.
        .ignoreException(ResilientPolicies::isOwnThrottling)
        // A source health signal, not a caller mistake: a plain 4xx (missing/bad request) means
        // the exchange itself succeeded, so it is recorded as a breaker success, not a failure.
        .recordException(ResilientPolicies::isBreakerFailure)
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
        .retryOnException(ResilientPolicies::isTransientFailure)
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
          .map(wait -> waitOrAbort(wait, retryAfterMaximumWait, responseException))
          .orElseGet(() -> jitteredBackoff.apply(attempt));
    }
    return jitteredBackoff.apply(attempt);
  }

  private static long waitOrAbort(Duration wait, Duration maximumWait, Throwable cause) {
    if (wait.compareTo(maximumWait) > 0) {
      throw new RetryAfterExceedsMaximumWaitException(
          "Retry-After %s exceeds the configured maximum wait %s".formatted(wait, maximumWait),
          cause);
    }
    return wait.toMillis();
  }

  private static boolean isOwnThrottling(Throwable throwable) {
    return throwable instanceof RequestNotPermitted
        || throwable instanceof AcquirePermissionCancelledException;
  }

  /**
   * Which failures count toward the breaker's failure rate. A response is only a source-health
   * signal when its status is one of the transient ones or any {@code 5xx}; a plain client-side
   * {@code 4xx} (missing resource, bad request) means the exchange itself succeeded and is
   * recorded as a breaker success, not a failure. Everything else that reaches here — connection
   * failures, an unacceptable response, a {@code Retry-After} beyond the configured maximum — is
   * a genuine failure.
   */
  private static boolean isBreakerFailure(Throwable throwable) {
    if (throwable instanceof HttpClientResponseException responseException) {
      int code = responseException.code();
      return code >= 500 || RETRYABLE_STATUSES.contains(code);
    }
    return true;
  }

  /**
   * The closed, explicit set of retryable failures: connection failures, resets, read timeouts,
   * and the transient statuses. {@link #isBreakerFailure} and this predicate are deliberately
   * independent — an unacceptable response or a non-transient status is recorded as a failure
   * above but never retried here.
   */
  private static boolean isTransientFailure(Throwable throwable) {
    if (throwable instanceof HttpClientResponseException responseException) {
      return RETRYABLE_STATUSES.contains(responseException.code());
    }
    return throwable instanceof ReadTimeoutException
        || throwable instanceof ResponseClosedException
        || throwable instanceof NoHostException
        // Any other named subtype (e.g. ContentLengthExceededException) is a structural mismatch
        // retrying cannot fix; only the plain, undecorated class means "connection unreachable".
        || throwable.getClass() == HttpClientException.class;
  }
}
