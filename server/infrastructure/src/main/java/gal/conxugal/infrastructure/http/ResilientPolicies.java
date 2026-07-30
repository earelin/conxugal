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

  /** Resilience4j's own floor: {@code IntervalFunction} rejects anything below one millisecond. */
  private static final Duration MINIMUM_BACKOFF = Duration.ofMillis(1);

  private ResilientPolicies() {}

  /**
   * Rejects a settings combination Resilience4j would only reject later, from inside the first
   * outbound call. Each of these otherwise surfaces as an {@code IllegalArgumentException} naming
   * a library-internal class, long after the process started cleanly.
   */
  static void validate(ResilientHttpClientSettings settings) {
    requireText(settings.baseUrl(), "baseUrl");
    requireAtLeast(settings.connectTimeout(), MINIMUM_BACKOFF, "connectTimeout");
    requireAtLeast(settings.readTimeout(), MINIMUM_BACKOFF, "readTimeout");
    requireAtLeast(
        settings.rateLimiterRefreshPeriod(), MINIMUM_BACKOFF, "rateLimiterRefreshPeriod");
    requireNotNegative(settings.maximumPermitWait(), "maximumPermitWait");
    requireAtLeast(settings.maxConcurrentCalls(), 1, "maxConcurrentCalls");
    requireAtLeast(settings.retryMaxAttempts(), 1, "retryMaxAttempts");
    requireAtLeast(settings.retryBaseDelay(), MINIMUM_BACKOFF, "retryBaseDelay");
    requireNotNegative(settings.retryAfterMaximumWait(), "retryAfterMaximumWait");
    requireInRange(settings.breakerFailureRateThreshold(), 1, 100, "breakerFailureRateThreshold");
    requireAtLeast(
        settings.breakerOpenStateDuration(), MINIMUM_BACKOFF, "breakerOpenStateDuration");
  }

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
        // trip slow-call detection as a false symptom of source health. A 100% rate is what
        // actually disables it — the duration threshold alone would still arm it on a default.
        .slowCallRateThreshold(100)
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
   * recorded as a breaker success, not a failure. A {@link ResponseMappingException} is the
   * caller's own parsing defect rather than anything the source did, so it is excluded too.
   * Everything else that reaches here — connection failures, an unacceptable response — is a
   * genuine failure.
   *
   * <p>A {@code Retry-After} beyond the configured maximum never reaches this predicate: it is
   * thrown from the retry interval function, outside the breaker's decorator. The response that
   * carried the header was already recorded here on its own status.
   */
  private static boolean isBreakerFailure(Throwable throwable) {
    if (throwable instanceof ResponseMappingException) {
      return false;
    }
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
        // Any other named subtype is either a structural mismatch retrying cannot fix
        // (ContentLengthExceededException) or a wiring defect that will fail identically every
        // time (NoHostException); only the plain, undecorated class means "connection
        // unreachable".
        || throwable.getClass() == HttpClientException.class;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(name));
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
      throw new IllegalArgumentException(
          "%s must not be negative, was %s".formatted(name, value));
    }
  }

  private static void requireInRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          "%s must be between %d and %d, was %d".formatted(name, minimum, maximum, value));
    }
  }
}
