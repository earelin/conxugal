package gal.conxugal.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import java.time.Clock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decorates a raw {@link BlockingHttpClient} with retry, rate-limiting and circuit-breaking
 * policies, composed longhand as {@code Retry(CircuitBreaker(RateLimiter(exchange)))} — the
 * limiter innermost so every attempt, retries included, consumes a permit; the breaker between, so
 * an open circuit rejects a call before it burns rate budget; retry outermost, matching
 * Resilience4j's own aspect order.
 *
 * <p>No Resilience4j type crosses this class's boundary: {@link RequestNotPermitted} and {@link
 * CallNotPermittedException} are translated to {@link HttpClientException}, the transport
 * exception type callers already handle, and both are excluded from retry.
 */
public final class ResilientHttpClient {

  static final String USER_AGENT_VALUE = "conxugal/1.0 (+https://github.com/earelin/conxugal)";

  static final int BREAKER_SLIDING_WINDOW_SIZE = ResilientPolicies.BREAKER_SLIDING_WINDOW_SIZE;
  static final int BREAKER_MINIMUM_NUMBER_OF_CALLS =
      ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS;

  private final BlockingHttpClient delegate;
  private final RateLimiter rateLimiter;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public ResilientHttpClient(
      String name, BlockingHttpClient delegate, ResilientHttpClientSettings settings) {
    this(name, delegate, settings, Clock.systemUTC());
  }

  ResilientHttpClient(
      String name,
      BlockingHttpClient delegate,
      ResilientHttpClientSettings settings,
      Clock clock) {
    this.delegate = delegate;
    this.rateLimiter = RateLimiter.of(name, ResilientPolicies.rateLimiterConfig(settings));
    this.circuitBreaker =
        CircuitBreaker.of(name, ResilientPolicies.circuitBreakerConfig(settings));
    this.retry = Retry.of(name, ResilientPolicies.retryConfig(settings, clock));
  }

  /** GET/HEAD-default overload: retryable per the method, nothing else. */
  public <T> T exchange(
      MutableHttpRequest<?> request,
      Function<HttpResponse<byte[]>, T> responseMapper,
      Predicate<T> isAcceptable) {
    return exchange(request, responseMapper, isAcceptable, false);
  }

  /** Full form: the caller declares idempotency explicitly for any non-GET/HEAD method. */
  public <T> T exchange(
      MutableHttpRequest<?> request,
      Function<HttpResponse<byte[]>, T> responseMapper,
      Predicate<T> isAcceptable,
      boolean requestIsIdempotent) {
    MutableHttpRequest<?> outgoing = request.header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);
    boolean retryable = isRetryableByMethod(request.getMethod()) || requestIsIdempotent;

    Supplier<T> exchangeAndMap =
        () -> {
          HttpResponse<byte[]> response = delegate.exchange(outgoing, byte[].class);
          T mapped = responseMapper.apply(response);
          if (!isAcceptable.test(mapped)) {
            throw new UnacceptableResponseException(
                "Response for %s %s failed the caller's acceptability check"
                    .formatted(request.getMethod(), request.getPath()));
          }
          return mapped;
        };

    Supplier<T> guarded =
        CircuitBreaker.decorateSupplier(
            circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, exchangeAndMap));
    Supplier<T> composed = retryable ? Retry.decorateSupplier(retry, guarded) : guarded;

    try {
      return composed.get();
    } catch (RequestNotPermitted e) {
      throw new HttpClientException(
          "Rate limiter refused a permit for %s %s"
              .formatted(request.getMethod(), request.getPath()),
          e);
    } catch (CallNotPermittedException e) {
      throw new HttpClientException(
          "Circuit breaker is open for %s %s".formatted(request.getMethod(), request.getPath()),
          e);
    }
  }

  private static boolean isRetryableByMethod(HttpMethod method) {
    return method == HttpMethod.GET || method == HttpMethod.HEAD;
  }
}
